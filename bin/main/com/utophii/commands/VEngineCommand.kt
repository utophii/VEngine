package com.utophii.commands

import com.utophii.VEngine
import com.utophii.api.EffectOptions
import com.utophii.engine.FXEngine
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class VEngineCommand(
    private val plugin: VEngine,
) : CommandExecutor, TabCompleter {

    private val mm = MiniMessage.miniMessage()

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
                sender.sendMessage(mm.deserialize(
                    "<red>Unknown subcommand. Use <gold>/vengine help</gold>.</red>"
                ))
                true
            }
        }
    }

    // ── /vengine help ────────────────────────────────────────────────────────

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(mm.deserialize(
            """
            <gradient:#7F5AF0:#2CB67D><bold>━━━ VEngine Commands ━━━</bold></gradient>
            <gold>/ve help</gold> <gray>— show this message</gray>
            <gold>/ve list</gold> <gray>— list all registered effects</gray>
            <gold>/ve play <effect> [scale] [duration]</gold> <gray>— spawn effect at your position</gray>
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
            sender.sendMessage(mm.deserialize("<yellow>No effects registered.</yellow>"))
            return true
        }

        sender.sendMessage(mm.deserialize(
            "<gradient:#7F5AF0:#2CB67D><bold>Registered Effects (${names.size})</bold></gradient>"
        ))
        names.forEach { name ->
            sender.sendMessage(mm.deserialize("  <aqua>• $name</aqua>"))
        }

        val active = FXEngine.activeHandles()
        if (active.isNotEmpty()) {
            sender.sendMessage(mm.deserialize(
                "<gradient:#FF5E62:#FF9966><bold>Active Running Effects (${active.size})</bold></gradient>"
            ))
            active.forEach { handle ->
                sender.sendMessage(mm.deserialize("  <yellow>• ${handle.id}</yellow> <gray>(${handle.effectName})</gray>"))
            }
        }
        return true
    }

    private fun cmdPlay(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERM_SPAWN)) {
            noPermission(sender); return true
        }
        if (sender !is Player) {
            sender.sendMessage(mm.deserialize("<red>Only players can spawn effects.</red>"))
            return true
        }
        if (args.size < 2) {
            sender.sendMessage(mm.deserialize(
                "<red>Usage: <gold>/vengine play <effect> [scale] [duration]</gold></red>"
            ))
            return true
        }

        val effectName = args[1].lowercase()

        if (FXEngine.effect(effectName) == null) {
            sender.sendMessage(mm.deserialize(
                "<red>Unknown effect <gold>'$effectName'</gold>. " +
                "Use <gold>/ve list</gold> to see available effects.</red>"
            ))
            return true
        }

        val scale = args.getOrNull(2)?.toDoubleOrNull() ?: DEFAULT_SCALE
        if (scale <= 0.0) {
            sender.sendMessage(mm.deserialize("<red>Scale must be > 0.</red>"))
            return true
        }

        val duration = args.getOrNull(3)?.toLongOrNull() ?: DEFAULT_DURATION_TICKS
        if (duration <= 0L) {
            sender.sendMessage(mm.deserialize("<red>Duration must be > 0 ticks.</red>"))
            return true
        }

        val opts = EffectOptions.builder()
            .scale(scale)
            .duration(duration)
            .build()

        val handle = FXEngine.play(effectName, sender.location, opts)

        sender.sendMessage(mm.deserialize(
            "<green>Playing effect <gold>'$effectName'</gold> " +
            "<gray>(ID: <yellow>${handle?.id ?: "unknown"}</yellow>)</gray> " +
            "for <aqua>${duration}</aqua> ticks at scale <aqua>$scale</aqua>.</green>"
        ))
        return true
    }

    private fun cmdStop(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERM_STOP)) {
            noPermission(sender); return true
        }

        if (args.size < 2 || args[1].equals("all", ignoreCase = true)) {
            val stoppedCount = FXEngine.stopAll()
            sender.sendMessage(mm.deserialize(
                "<green>Stopped <gold>$stoppedCount</gold> active effect(s).</green>"
            ))
            return true
        }

        val targetId = args[1]
        val stopped = FXEngine.stop(targetId)
        if (stopped) {
            sender.sendMessage(mm.deserialize(
                "<green>Successfully stopped effect <gold>'$targetId'</gold>.</green>"
            ))
        } else {
            sender.sendMessage(mm.deserialize(
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
        sender.sendMessage(mm.deserialize(
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

            args.size == 3 && args[0].equals("play", ignoreCase = true) ->
                SCALE_HINTS.filter { it.startsWith(args[2]) }

            args.size == 4 && args[0].equals("play", ignoreCase = true) ->
                DURATION_HINTS.filter { it.startsWith(args[3]) }

            else -> emptyList()
        }
    }

    private fun noPermission(sender: CommandSender) {
        sender.sendMessage(mm.deserialize(
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

        private val SUBCOMMANDS    = listOf("help", "list", "play", "stop", "reload")
        private val SCALE_HINTS    = listOf("0.5", "1.0", "1.5", "2.0", "3.0")
        private val DURATION_HINTS = listOf("20", "40", "60", "100", "200", "400")
    }
}