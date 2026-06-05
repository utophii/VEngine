package com.utophii.commands

import com.utophii.VEngine
import com.utophii.api.EffectOptions
import com.utophii.config.YamlEffectLoader
import com.utophii.engine.FXEngine
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class VEngineCommand(
    private val plugin: VEngine,
    private val loader: YamlEffectLoader,
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

        // validate effect exists
        if (FXEngine.effect(effectName) == null) {
            sender.sendMessage(mm.deserialize(
                "<red>Unknown effect <gold>'$effectName'</gold>. " +
                "Use <gold>/ve list</gold> to see available effects.</red>"
            ))
            return true
        }

        // parse optional scale - default 1.0
        val scale = args.getOrNull(2)?.toDoubleOrNull()
            ?: DEFAULT_SCALE
        if (scale <= 0.0) {
            sender.sendMessage(mm.deserialize("<red>Scale must be > 0.</red>"))
            return true
        }

        // parse optional duration in ticks - default 60 (3 s)
        val duration = args.getOrNull(3)?.toLongOrNull()
            ?: DEFAULT_DURATION_TICKS
        if (duration <= 0L) {
            sender.sendMessage(mm.deserialize("<red>Duration must be > 0 ticks.</red>"))
            return true
        }

        val opts = EffectOptions.builder()
            .scale(scale)
            .duration(duration)
            .build()

        // dispatch to FXEngine - heavy math runs async, spawnParticle runs sync
        FXEngine.play(effectName, sender.location, opts)

        sender.sendMessage(mm.deserialize(
            "<green>Playing effect <gold>'$effectName'</gold> " +
            "for <aqua>${duration}</aqua> ticks at scale <aqua>$scale</aqua>.</green>"
        ))
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
            // first arg: subcommand
            args.size == 1 -> SUBCOMMANDS
                .filter { it.startsWith(args[0].lowercase()) }

            // second arg (only for 'play'): effect name
            args.size == 2 && args[0].equals("play", ignoreCase = true) ->
                FXEngine.effectNames()
                    .filter { it.startsWith(args[1].lowercase()) }

            // third arg (play): scale hint
            args.size == 3 && args[0].equals("play", ignoreCase = true) ->
                SCALE_HINTS.filter { it.startsWith(args[2]) }

            // fourth arg (play): duration hint
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
        private const val PERM_RELOAD = "vengine.reload"
        private const val PERM_LIST   = "vengine.list"

        // uniform scale applied when none is provided by the caller
        private const val DEFAULT_SCALE          = 1.0

        // default animation duration: 3 seconds at 20 ticks/s
        private const val DEFAULT_DURATION_TICKS = 60L

        private val SUBCOMMANDS    = listOf("help", "list", "play", "reload")
        private val SCALE_HINTS    = listOf("0.5", "1.0", "1.5", "2.0", "3.0")
        private val DURATION_HINTS = listOf("20", "40", "60", "100", "200", "400")
    }
}