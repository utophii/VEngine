package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.Hash
import com.utophii.math.ImplicitSurface
import com.utophii.math.ScalarField
import org.bukkit.Location
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// A user-defined implicit surface f(x, y, z) = 0, usually a signed distance function (SDF) built from
// sdSphere/sdBox/sdTorus/smin/smax, but any formula works (a gyroid: sin(x)cos(y) + sin(y)cos(z) + sin(z)cos(x)).
// Every particle owns a fixed seed in the cube [-bounds, bounds]³ and is projected onto the surface each frame,
// so a formula that uses time morphs while the particles glide along it. Seeds that do not reach the surface
// inside the cube are dropped. The gradient value is the value formula of the surface point, or its height.
// Formulas without time give the same points every frame, so those are projected once per parameter set and cached
class ImplicitEffect(
    name: String,
    private val formula: String,
    private val valueFormula: String?,
    private val bounds: Double,
    private val defaultPoints: Int,
    private val defaults: Map<String, Double>,
    private val angularSpeed: Double,
    override val followDefaults: FollowDefaults? = null,
) : AbstractParticleEffect(name), ConfiguredPrimitive {

    // compiled once and shared across concurrent plays
    private val field: ExpressionEvaluator = requireNotNull(ExpressionEvaluator.compile(formula)) { "Invalid formula: '$formula'" }
    private val value: ExpressionEvaluator? = valueFormula?.let { requireNotNull(ExpressionEvaluator.compile(it)) { "Invalid formula: '$it'" } }
    private val animated = TIME_VARIABLE in field.variableNames || TIME_VARIABLE in value?.variableNames.orEmpty()

    // projected local points of static surfaces, shared by concurrent frames
    private val cache = ConcurrentHashMap<CacheKey, DoubleArray>()

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val points = (opts.parameters[POINTS_PARAM]?.toInt() ?: defaultPoints).coerceIn(MIN_POINTS, MAX_POINTS)
        val extent = (opts.parameters[BOUNDS_PARAM] ?: bounds).coerceAtLeast(MIN_BOUNDS)
        val phase = time * (opts.parameters[ANGULAR_SPEED_PARAM] ?: angularSpeed)
        // params = defaults + runtime options
        val params = HashMap<String, Double>(defaults.size + opts.parameters.size + 1)
        params.putAll(defaults)
        params.putAll(opts.parameters)

        val surfacePoints = if (animated) {
            params[TIME_VARIABLE] = time
            project(params, points, extent)
        } else {
            val key = CacheKey(params, points, extent)
            cache[key] ?: project(params, points, extent).also { projected ->
                // animated parameters could otherwise grow the cache without bound
                if (cache.size >= MAX_CACHED_SURFACES) cache.clear()
                cache[key] = projected
            }
        }

        val count = surfacePoints.size / STRIDE
        buffer.acquire(count)
        for (index in 0 until count) {
            val offset = index * STRIDE
            writeParticle(
                buffer, index, surfacePoints[offset], surfacePoints[offset + 1], surfacePoints[offset + 2],
                center, opts, time, center, opts.rotationYaw + phase,
            )
            buffer.value(index, surfacePoints[offset + VALUE_OFFSET])
        }
    }

    // projects the seeds onto the surface, returns x, y, z and the gradient value of every surface point
    private fun project(params: Map<String, Double>, points: Int, extent: Double): DoubleArray {
        // per-frame copies
        val distance = field.fork().also { it.bind(params) }
        val shade = value?.fork()?.also { it.bind(params) }
        val surface = ScalarField { x, y, z ->
            distance.set(X, x)
            distance.set(Y, y)
            distance.set(Z, z)
            distance.evaluate()
        }
        val accept = extent * ACCEPT_RATIO
        val limit = extent * BOUNDS_SLACK
        val point = DoubleArray(3)
        val result = DoubleArray(points * STRIDE)

        var written = 0
        for (index in 0 until points) {
            var seed = Hash.mix(index.toLong())
            point[0] = Hash.signedUnit(seed) * extent
            seed = Hash.mix(seed)
            point[1] = Hash.signedUnit(seed) * extent
            seed = Hash.mix(seed)
            point[2] = Hash.signedUnit(seed) * extent
            val residual = ImplicitSurface.project(surface, point, PROJECTION_STEPS, extent * MAX_STEP_RATIO)
            val x = point[0]
            val y = point[1]
            val z = point[2]
            if (residual > accept || residual.isNaN() || abs(x) > limit || abs(y) > limit || abs(z) > limit) {
                continue
            }
            val offset = written * STRIDE
            result[offset] = x
            result[offset + 1] = y
            result[offset + 2] = z
            result[offset + VALUE_OFFSET] = if (shade != null) {
                shade.set(X, x)
                shade.set(Y, y)
                shade.set(Z, z)
                shade.evaluate()
            } else {
                // height in the cube, 0 at the bottom and 1 at the top
                (y + extent) / (2.0 * extent)
            }
            written++
        }
        return result.copyOf(written * STRIDE)
    }

    private data class CacheKey(val params: Map<String, Double>, val points: Int, val extent: Double)

    companion object {
        private const val POINTS_PARAM = "points"
        private const val BOUNDS_PARAM = "bounds"
        private const val ANGULAR_SPEED_PARAM = "angularSpeed"
        private const val TIME_VARIABLE = "time"
        private const val X = "x"
        private const val Y = "y"
        private const val Z = "z"

        // x, y, z, value per surface point
        private const val STRIDE = 4
        private const val VALUE_OFFSET = 3
        private const val MAX_CACHED_SURFACES = 32

        private const val MIN_POINTS = 1
        private const val MAX_POINTS = 4096
        private const val MIN_BOUNDS = 0.01
        private const val PROJECTION_STEPS = 16

        // per-step move and accepted |f|, relative to the sampled cube
        private const val MAX_STEP_RATIO = 0.25
        private const val ACCEPT_RATIO = 0.002

        // points may end slightly outside the seed cube
        private const val BOUNDS_SLACK = 1.05
    }
}
