package com.utophii.commands

import com.utophii.VEngine
import com.utophii.api.EffectOptions
import com.utophii.api.FollowOptions
import com.utophii.engine.FXEngine
import com.utophii.util.LegacyFormat
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.BlockCommandSender
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class VEngineCommand(
    private val plugin: VEngine,
) : CommandExecutor, TabCompleter {


    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "help"   -> { sendHelp(sender); true }
            "list"   -> cmdList(sender)
            "play"   -> cmdPlay(sender, args)
            "stop"   -> cmdStop(sender, args)
            "reload" -> cmdReload(sender)
            else     -> {
                sender.sendMessage(LegacyFormat.format(
                    "<red>Unknown subcommand. Use <gold>/vengine help</gold>.</red>"
                ))
                true
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(LegacyFormat.format(
            """
            <gradient:#7F5AF0:#2CB67D><bold>━━━ VEngine Commands ━━━</bold></gradient>
            <gold>/ve help</gold> <gray>— show this message</gray>
            <gold>/ve list</gold> <gray>— list all registered effects</gray>
            <gold>/ve play <effect> [x y z [world]] [scale] [duration]</gold> <gray>— spawn effect at your position or the given one</gray>
            <gold>/ve stop [all|<id>]</gold> <gray>— stop active running effect(s)</gray>
            <gold>/ve reload</gold> <gray>— reload effect YAML files</gray>
            """.trimIndent()
        ))
    }

    private fun cmdList(sender: CommandSender): Boolean {
        if (!sender.hasPermission(PERM_LIST)) {
            noPermission(sender); return true
        }

        val names = FXEngine.effectNames()
        if (names.isEmpty()) {
            sender.sendMessage(LegacyFormat.format("<yellow>No effects registered.</yellow>"))
            return true
        }

        sender.sendMessage(LegacyFormat.format(
            "<gradient:#7F5AF0:#2CB67D><bold>Registered Effects (${names.size})</bold></gradient>"
        ))
        names.forEach { name ->
            sender.sendMessage(LegacyFormat.format("  <aqua>• $name</aqua>"))
        }

        val active = FXEngine.activeHandles()
        if (active.isNotEmpty()) {
            sender.sendMessage(LegacyFormat.format(
                "<gradient:#FF5E62:#FF9966><bold>Active Running Effects (${active.size})</bold></gradient>"
            ))
            active.forEach { handle ->
                sender.sendMessage(LegacyFormat.format("  <yellow>• ${handle.id}</yellow> <gray>(${handle.effectName})</gray>"))
            }
        }
        return true
    }

    private fun cmdPlay(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERM_SPAWN)) {
            noPermission(sender); return true
        }
        if (args.size < 2) {
            sender.sendMessage(LegacyFormat.format("<red>Usage: <gold>$PLAY_USAGE</gold></red>"))
            return true
        }

        val effectName = args[1].lowercase()
        val effect = FXEngine.effect(effectName)

        if (effect == null) {
            sender.sendMessage(LegacyFormat.format(
                "<red>Unknown effect <gold>'$effectName'</gold>. " +
                "Use <gold>/ve list</gold> to see available effects.</red>"
            ))
            return true
        }

        // [x y z world] comes before scale duration, three coordinate-looking args mean a position
        var next = 2
        val origin = senderLocation(sender)
        val target = if (args.size >= next + 3 && (next until next + 3).all { isCoordinate(args[it]) }) {
            val coords = args.copyOfRange(next, next + 3)
            next += 3
            val worldName = args.getOrNull(next)?.takeIf { it.toDoubleOrNull() == null }
            if (worldName != null) next++
            parseLocation(sender, origin, coords, worldName) ?: return true
        } else {
            origin ?: run {
                sender.sendMessage(LegacyFormat.format(
                    "<red>Specify a position: <gold>$PLAY_USAGE</gold></red>"
                ))
                return true
            }
        }

        val scale = args.getOrNull(next)?.toDoubleOrNull() ?: DEFAULT_SCALE
        if (scale <= 0.0) {
            sender.sendMessage(LegacyFormat.format("<red>Scale must be > 0.</red>"))
            return true
        }

        val duration = args.getOrNull(next + 1)?.toLongOrNull() ?: DEFAULT_DURATION_TICKS
        if (duration <= 0L) {
            sender.sendMessage(LegacyFormat.format("<red>Duration must be > 0 ticks.</red>"))
            return true
        }

        val player = sender as? Player
        // effects with a follow: block stick to the player who played them
        val positioned = next > 2
        val follow = effect.followDefaults?.takeUnless { positioned }?.let { player?.let(::FollowOptions) }
        val opts = EffectOptions.builder()
            .scale(scale)
            .duration(duration)
            .follow(follow)
            .subject(player)
            .build()

        val handle = FXEngine.play(effectName, target, opts)

        // MythicMobs and command blocks run this often
        if (player != null) {
            sender.sendMessage(LegacyFormat.format(
                "<green>Playing effect <gold>'$effectName'</gold> " +
                "<gray>(ID: <yellow>${handle?.id ?: "unknown"}</yellow>)</gray> " +
                "for <aqua>${duration}</aqua> ticks at scale <aqua>$scale</aqua>.</green>"
            ))
        }
        return true
    }

    // where ~ is relative to: the player, the command block or the entity running the command
    private fun senderLocation(sender: CommandSender): Location? = when (sender) {
        is Entity -> sender.location
        is BlockCommandSender -> sender.block.location.add(0.5, 0.0, 0.5)
        else -> null
    }

    private fun parseLocation(sender: CommandSender, origin: Location?, coords: Array<out String>, worldName: String?): Location? {
        val world = if (worldName != null) {
            Bukkit.getWorld(worldName) ?: run {
                sender.sendMessage(LegacyFormat.format("<red>Unknown world <gold>'$worldName'</gold>.</red>"))
                return null
            }
        } else {
            origin?.world ?: Bukkit.getWorlds().first()
        }
        val base = doubleArrayOf(origin?.x ?: 0.0, origin?.y ?: 0.0, origin?.z ?: 0.0)
        val values = DoubleArray(3)
        for (i in 0 until 3) {
            val raw = coords[i]
            if (raw.startsWith('~')) {
                if (origin == null) {
                    sender.sendMessage(LegacyFormat.format("<red>Relative coordinates need a position to start from.</red>"))
                    return null
                }
                values[i] = base[i] + (raw.substring(1).takeIf(String::isNotEmpty)?.toDouble() ?: 0.0)
            } else {
                values[i] = raw.toDouble()
            }
        }
        // keeps the sender's facing, effects oriented by yaw/pitch look the same as before
        return Location(world, values[0], values[1], values[2], origin?.yaw ?: 0f, origin?.pitch ?: 0f)
    }

    private fun isCoordinate(arg: String): Boolean =
        arg.toDoubleOrNull()?.isFinite() == true ||
            (arg.startsWith('~') && (arg.length == 1 || arg.substring(1).toDoubleOrNull()?.isFinite() == true))

    private fun cmdStop(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERM_STOP)) {
            noPermission(sender); return true
        }

        if (args.size < 2 || args[1].equals("all", ignoreCase = true)) {
            val stoppedCount = FXEngine.stopAll()
            sender.sendMessage(LegacyFormat.format(
                "<green>Stopped <gold>$stoppedCount</gold> active effect(s).</green>"
            ))
            return true
        }

        val targetId = args[1]
        val stopped = FXEngine.stop(targetId)
        if (stopped) {
            sender.sendMessage(LegacyFormat.format(
                "<green>Successfully stopped effect <gold>'$targetId'</gold>.</green>"
            ))
        } else {
            sender.sendMessage(LegacyFormat.format(
                "<red>No active effect found matching ID <gold>'$targetId'</gold>.</red>"
            ))
        }
        return true
    }

    private fun cmdReload(sender: CommandSender): Boolean {
        if (!sender.hasPermission(PERM_RELOAD)) {
            noPermission(sender); return true
        }
        plugin.reload()
        sender.sendMessage(LegacyFormat.format(
            "<green>VEngine effects reloaded successfully.</green>"
        ))
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        return when {
            args.size == 1 -> SUBCOMMANDS
                .filter { it.startsWith(args[0].lowercase()) }

            args.size == 2 && args[0].equals("play", ignoreCase = true) ->
                FXEngine.effectNames()
                    .filter { it.startsWith(args[1].lowercase()) }

            args.size == 2 && args[0].equals("stop", ignoreCase = true) -> {
                val suggestions = mutableListOf("all")
                suggestions.addAll(FXEngine.activeHandles().map { it.id })
                suggestions.filter { it.startsWith(args[1], ignoreCase = true) }
            }

            args.size >= 3 && args[0].equals("play", ignoreCase = true) -> playHints(args)
                .filter { it.startsWith(args.last(), ignoreCase = true) }

            else -> emptyList()
        }
    }

    // after the effect: x y z (or scale duration), then world, scale and duration once a position is typed
    private fun playHints(args: Array<out String>): List<String> {
        val index = args.size - 1
        if (index < 5) {
            return listOf("~") + when (index) {
                2 -> SCALE_HINTS
                3 -> DURATION_HINTS
                else -> emptyList()
            }
        }
        if (!(2 until 5).all { isCoordinate(args[it]) }) {
            return emptyList()
        }
        val afterWorld = if (index > 5 && args[5].toDoubleOrNull() == null) 6 else 5
        return when (index - afterWorld) {
            0 -> if (afterWorld == 5) Bukkit.getWorlds().map { it.name } + SCALE_HINTS else SCALE_HINTS
            1 -> DURATION_HINTS
            else -> emptyList()
        }
    }

    private fun noPermission(sender: CommandSender) {
        sender.sendMessage(LegacyFormat.format(
            "<red>You do not have permission to run this command.</red>"
        ))
    }

    companion object {
        private const val PERM_SPAWN  = "vengine.effect.spawn"
        private const val PERM_STOP   = "vengine.effect.stop"
        private const val PERM_RELOAD = "vengine.reload"
        private const val PERM_LIST   = "vengine.list"

        private const val DEFAULT_SCALE = 1.0
        private const val DEFAULT_DURATION_TICKS = 60L

        private const val PLAY_USAGE = "/vengine play <effect> [x y z [world]] [scale] [duration]"

        private val SUBCOMMANDS    = listOf("help", "list", "play", "stop", "reload")
        private val SCALE_HINTS    = listOf("0.5", "1.0", "1.5", "2.0", "3.0")
        private val DURATION_HINTS = listOf("20", "40", "60", "100", "200", "400")
    }
}