package com.utophii.engine

import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

// turns one %placeholder% into its text for a player
fun interface PlaceholderResolver {
    fun resolve(player: Player?, placeholder: String): String
}

// placeholder values for effect formulas and texts
object Placeholders {
    @Volatile
    var resolver: PlaceholderResolver? = null

    // each problem is logged once until the next reload
    private val warned = ConcurrentHashMap.newKeySet<String>()

    // call on the server thread
    fun value(player: Player?, placeholder: String): Double {
        val current = resolver ?: run {
            warnOnce("Placeholder $placeholder is used, but PlaceholderAPI is not installed; using 0.")
            return MISSING_VALUE
        }
        val text = runCatching { current.resolve(player, placeholder) }.getOrElse { error ->
            warnOnce("Placeholder $placeholder failed (${error.message}); using 0.")
            return MISSING_VALUE
        }
        return text.trim().toDoubleOrNull() ?: run {
            val reason = if (text == placeholder) "is unknown to PlaceholderAPI" else "returned '$text', which is not a number"
            warnOnce("Placeholder $placeholder $reason; using 0.")
            MISSING_VALUE
        }
    }

    fun containsPlaceholders(text: String): Boolean = PATTERN.containsMatchIn(text)

    // replaces every %placeholder% in text, call on the server thread
    fun text(player: Player?, text: String): String {
        if (!containsPlaceholders(text)) {
            return text
        }
        val current = resolver ?: run {
            warnOnce("Placeholders in '$text' are used, but PlaceholderAPI is not installed; they are left as written.")
            return text
        }
        return PATTERN.replace(text) { match ->
            runCatching { current.resolve(player, match.value) }.getOrElse { error ->
                warnOnce("Placeholder ${match.value} failed (${error.message}); it is left as written.")
                match.value
            }
        }
    }

    fun resetWarnings() {
        warned.clear()
    }

    private fun warnOnce(message: String) {
        if (warned.add(message)) {
            logger().warning(message)
        }
    }

    private fun logger(): Logger =
        runCatching { FXEngine.scheduler().plugin.logger }.getOrElse { Logger.getLogger(LOGGER_NAME) }

    private const val MISSING_VALUE = 0.0

    // starts with a letter, as in PlaceholderExpression
    private val PATTERN = Regex("%[A-Za-z][A-Za-z0-9_:.]*%")
    private const val LOGGER_NAME = "VEngine"
}
