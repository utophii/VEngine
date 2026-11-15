package com.utophii.commands

import com.utophii.config.EffectValidation
import com.utophii.config.ValidationIssue
import com.utophii.effects.ScriptedEffect
import com.utophii.engine.FXEngine
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.*

class VEngineCommandTest {
    private val messages = mutableListOf<String>()
    private val logged = mutableListOf<String>()
    private var permitted = true
    private var validations = 0
    private var reloads = 0
    private var reports = listOf(EffectValidation("draft.yml", "renamed", emptyList()))
    private val handler = VEngineCommand(
        reload = { reloads++ },
        validateEffects = { validations++; reports },
        effectFileNames = { listOf("draft", "broken") },
        version = { "test-version" },
        logDiagnostic = logged::add,
    )
    private val sender = Proxy.newProxyInstance(
        CommandSender::class.java.classLoader, arrayOf(CommandSender::class.java),
    ) { _, method, args ->
        when (method.name) {
            "hasPermission" -> permitted
            "sendMessage" -> {
                args.orEmpty().forEach { arg ->
                    when (arg) {
                        is String -> messages += arg
                        is Array<*> -> messages += arg.filterIsInstance<String>()
                    }
                }
                null
            }
            "toString" -> "Test sender"
            else -> error("Unexpected sender method: ${method.name}")
        }
    } as CommandSender
    private val command = object : Command("vengine") {
        override fun execute(sender: CommandSender, commandLabel: String, args: Array<out String>) = true
    }

    @AfterEach fun cleanup() = FXEngine.shutdown()

    private fun run(vararg args: String) {
        assertTrue(handler.onCommand(sender, command, "ve", args))
    }

    @Test fun `validate accepts IDs and filenames without reloading`() {
        for (target in listOf("renamed", "RENAMED", "draft", "draft.yml")) {
            messages.clear()
            run("validate", target)
            assertTrue(messages.any { "1 file(s), 0 error(s)" in it }, messages.toString())
        }
        assertEquals(4, validations)
        assertEquals(0, reloads)
    }

    @Test fun `denied commands do not inspect files or disclose engine details`() {
        permitted = false
        run("validate")
        run("info")
        assertEquals(0, validations)
        assertEquals(2, messages.size)
        assertTrue(messages.all { "permission" in it })
        val suggestions = handler.onTabComplete(sender, command, "ve", arrayOf(""))
        assertFalse("validate" in suggestions)
        assertFalse("info" in suggestions)
    }

    @Test fun `invalid arguments and unknown files have clear messages`() {
        run("validate", "one", "two")
        assertEquals(0, validations)
        assertTrue(messages.single().contains("Usage"))
        messages.clear()
        run("validate", "absent")
        assertTrue(messages.single().contains("No effect YAML file matches"))
        messages.clear()
        run("info", "absent")
        assertTrue(messages.single().contains("Unknown loaded effect"))
    }

    @Test fun `validation reports severity and sends overflow to the log`() {
        reports = listOf(EffectValidation("bad.yml", "bad", List(22) { ValidationIssue("problem $it", warning = it == 0) }))
        run("validate")
        assertTrue(messages.first().contains("21 error(s), 1 warning(s)"))
        assertEquals(20, messages.count { "[ERROR]" in it || "[WARN]" in it })
        assertEquals(22, logged.size)
        assertTrue(messages.any { "server log" in it })
    }

    @Test fun `info shows engine statistics and loaded effect metadata`() {
        FXEngine.registerScripted(ScriptedEffect("loaded", 120, emptyList()))
        run("info")
        assertTrue(messages.any { "test-version" in it })
        assertTrue(messages.any { "Registered effects: 1" in it })
        messages.clear()
        run("info", "LOADED")
        assertTrue(messages.any { "Effect: loaded" in it })
        assertTrue(messages.any { "default duration: 120 ticks" in it })
        assertTrue(messages.any { "Running instances: 0" in it })
    }

    @Test fun `completion includes broken files for validation and loaded effects for info`() {
        FXEngine.registerScripted(ScriptedEffect("loaded", 120, emptyList()))
        assertEquals(listOf("broken"), handler.onTabComplete(sender, command, "ve", arrayOf("validate", "br")))
        assertEquals(listOf("loaded"), handler.onTabComplete(sender, command, "ve", arrayOf("info", "lo")))
        assertEquals(emptyList(), handler.onTabComplete(sender, command, "ve", arrayOf("info", "br")))
    }
}
