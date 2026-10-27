package com.utophii.shapes

import com.utophii.api.EffectOptions
import com.utophii.engine.Placeholders
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// The geometry of a shape, which for a text with %placeholders% differs per play.
// The template's geometry is built once, when the effect loads. A play resolves the placeholders for its player
// and passes the text on as a hidden numeric parameter, and the text's geometry is built on first use
class ShapeVariants<T : Any> private constructor(
    private val template: String,
    private val build: (String) -> T,
) {
    val base: T = build(template)

    private val hasPlaceholders = Placeholders.containsPlaceholders(template)
    private val ids = ConcurrentHashMap<String, Int>()
    private val texts = ConcurrentHashMap<Int, String>()
    private val built = ConcurrentHashMap<Int, T>()
    private val nextId = AtomicInteger()

    // call on the server thread, where placeholders can be read
    fun playParameters(configured: Map<String, Double>, subject: Player?): Map<String, Double> {
        if (!hasPlaceholders || VARIANT_PARAM in configured) {
            return emptyMap()
        }
        val text = Placeholders.text(subject, template)
        if (text == template) {
            return emptyMap()
        }
        // one text per online player is the usual load, a cache that keeps growing is reset
        if (ids.size >= MAX_TEXTS) {
            ids.clear()
            texts.clear()
            built.clear()
        }
        val id = ids.computeIfAbsent(text) { nextId.incrementAndGet().also { id -> texts[id] = text } }
        return mapOf(VARIANT_PARAM to id.toDouble())
    }

    // the geometry of the play's text, a text that cannot be drawn falls back to the template's
    fun resolve(opts: EffectOptions): T {
        val id = opts.parameters[VARIANT_PARAM]?.toInt() ?: return base
        built[id]?.let { return it }
        val text = texts[id] ?: return base
        val geometry = runCatching { build(text) }.getOrDefault(base)
        built[id] = geometry
        return geometry
    }

    companion object {
        private const val VARIANT_PARAM = "textVariant"
        private const val MAX_TEXTS = 512

        fun <T : Any> fixed(geometry: T): ShapeVariants<T> = ShapeVariants("") { geometry }

        fun <T : Any> text(template: String, build: (String) -> T): ShapeVariants<T> = ShapeVariants(template, build)
    }
}
