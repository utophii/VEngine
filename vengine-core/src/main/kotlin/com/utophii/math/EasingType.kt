package com.utophii.math

// supported types of easing functions for animation interpolation
enum class EasingType {
    LINEAR,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC,
    EASE_OUT_CUBIC,
    EASE_IN_OUT_CUBIC,
    EASE_IN_SINE,
    EASE_OUT_SINE,
    EASE_IN_OUT_SINE,
    EASE_IN_ELASTIC,
    EASE_OUT_ELASTIC,
    EASE_IN_BOUNCE,
    EASE_OUT_BOUNCE,
    EASE_IN_OUT_BOUNCE;

    // transforms a normalized factor t ∈ [0, 1] along a smooth curve
    fun apply(t: Double): Double = when (this) {
        LINEAR -> t.coerceIn(0.0, 1.0)
        EASE_IN_QUAD -> AdvancedMathUtils.easeInQuad(t)
        EASE_OUT_QUAD -> AdvancedMathUtils.easeOutQuad(t)
        EASE_IN_OUT_QUAD -> AdvancedMathUtils.easeInOutQuad(t)
        EASE_IN_CUBIC -> AdvancedMathUtils.easeInCubic(t)
        EASE_OUT_CUBIC -> AdvancedMathUtils.easeOutCubic(t)
        EASE_IN_OUT_CUBIC -> AdvancedMathUtils.easeInOutCubic(t)
        EASE_IN_SINE -> AdvancedMathUtils.easeInSine(t)
        EASE_OUT_SINE -> AdvancedMathUtils.easeOutSine(t)
        EASE_IN_OUT_SINE -> AdvancedMathUtils.easeInOutSine(t)
        EASE_IN_ELASTIC -> AdvancedMathUtils.easeInElastic(t)
        EASE_OUT_ELASTIC -> AdvancedMathUtils.easeOutElastic(t)
        EASE_IN_BOUNCE -> AdvancedMathUtils.easeInBounce(t)
        EASE_OUT_BOUNCE -> AdvancedMathUtils.easeOutBounce(t)
        EASE_IN_OUT_BOUNCE -> AdvancedMathUtils.easeInOutBounce(t)
    }

    companion object {
        // EasingType case-insensitive and separator-insensitive parser
        fun fromString(name: String?): EasingType {
            if (name.isNullOrBlank()) return LINEAR
            val normalized = name.trim().lowercase().replace("-", "_")
            return when (normalized) {
                "linear" -> LINEAR
                "ease_in_quad", "easeinquad", "quad_in", "quadin" -> EASE_IN_QUAD
                "ease_out_quad", "easeoutquad", "quad_out", "quadout" -> EASE_OUT_QUAD
                "ease_in_out_quad", "easeinoutquad", "quad_in_out", "quadinout", "quad" -> EASE_IN_OUT_QUAD
                "ease_in_cubic", "easeincubic", "cubic_in", "cubicin" -> EASE_IN_CUBIC
                "ease_out_cubic", "easeoutcubic", "cubic_out", "cubicout" -> EASE_OUT_CUBIC
                "ease_in_out_cubic", "easeinoutcubic", "cubic_in_out", "cubicinout", "cubic" -> EASE_IN_OUT_CUBIC
                "ease_in_sine", "easeinsine", "sine_in", "sinein" -> EASE_IN_SINE
                "ease_out_sine", "easeoutsine", "sine_out", "sineout" -> EASE_OUT_SINE
                "ease_in_out_sine", "easeinoutsine", "sine_in_out", "sineinout", "sine" -> EASE_IN_OUT_SINE
                "ease_in_elastic", "easeinelastic", "elastic_in", "elasticin" -> EASE_IN_ELASTIC
                "ease_out_elastic", "easeoutelastic", "elastic_out", "elasticout", "elastic" -> EASE_OUT_ELASTIC
                "ease_in_bounce", "easeinbounce", "bounce_in", "bouncein" -> EASE_IN_BOUNCE
                "ease_out_bounce", "easeoutbounce", "bounce_out", "bounceout", "bounce" -> EASE_OUT_BOUNCE
                "ease_in_out_bounce", "easeinoutbounce", "bounce_in_out", "bounceinout" -> EASE_IN_OUT_BOUNCE
                else -> LINEAR
            }
        }
    }
}