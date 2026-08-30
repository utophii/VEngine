package com.utophii.math

import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// mathematical formulas used by VEngine effects and modifiers
object MathUtils {
    const val TAU = PI * 2.0
    const val HALF = 0.5
    const val GOLDEN_ANGLE = PI * (3.0 - 2.23606797749979)
    const val MIN_CURL_EPSILON = 1.0E-4
    const val MIN_CURL_FREQUENCY = 0.0
    private const val HASH_SCALE = 43758.5453123
    private const val HASH_X = 127.1
    private const val HASH_Y = 311.7
    private const val HASH_Z = 74.7
    private const val CURL_TIME_SPEED = 0.07
    private const val CURL_POTENTIAL_X_OFFSET = 19.19
    private const val CURL_POTENTIAL_Y_OFFSET = 37.37
    private const val CURL_POTENTIAL_Z_OFFSET = 53.53

    // calculates a helix point
    // https://en.wikipedia.org/wiki/Helix x = r cos(t), y = h t, z = r sin(t)
    fun helix(radius: Double, heightStep: Double, t: Double): Vector {
        // x = r * cos(t): projects circular motion onto the X axis
        val x = radius * cos(t)
        // y = h * t: raises the curve linearly as the parameter advances
        val y = heightStep * t
        // z = r * sin(t): projects circular motion onto the Z axis
        val z = radius * sin(t)
        return Vector(x, y, z)
    }

    // calculates a torus point
    // https://en.wikipedia.org/wiki/Torus x = (R + r cos(phi)) cos(theta)
    fun torus(majorRadius: Double, minorRadius: Double, theta: Double, phi: Double): Vector {
        // tubeRadius = R + r * cos(phi): distance from torus center to the tube point projected onto XZ
        val tubeRadius = majorRadius + minorRadius * cos(phi)
        // x = (R + r * cos(phi)) * cos(theta): rotates the tube projection around Y
        val x = tubeRadius * cos(theta)
        // y = r * sin(phi): moves around the minor circle vertically
        val y = minorRadius * sin(phi)
        // z = (R + r * cos(phi)) * sin(theta): rotates the tube projection around Y
        val z = tubeRadius * sin(theta)
        return Vector(x, y, z)
    }

    // calculates a Lissajous point in the XZ plane
    // https://en.wikipedia.org/wiki/Lissajous_curve x = A sin(a t + delta), z = B sin(b t)
    fun lissajous(amplitudeX: Double, amplitudeZ: Double, a: Double, b: Double, delta: Double, t: Double): Vector {
        // x = A * sin(a * t + delta): harmonic motion on X with phase shift.
        val x = amplitudeX * sin(a * t + delta)
        // z = B * sin(b * t): harmonic motion on Z.
        val z = amplitudeZ * sin(b * t)
        return Vector(x, 0.0, z)
    }

    // calculates an epicycloid point
    // https://en.wikipedia.org/wiki/Epicycloid x = (R + r) cos(t) - d cos((R + r) / r t)
    fun epicycloid(majorRadius: Double, minorRadius: Double, distance: Double, t: Double): Vector {
        // ratio = (R + r) / r: angular speed multiplier for the tracing point.
        val ratio = (majorRadius + minorRadius) / minorRadius
        // x = (R + r) * cos(t) - d * cos(ratio * t): epicycloid horizontal coordinate.
        val x = (majorRadius + minorRadius) * cos(t) - distance * cos(ratio * t)
        // z = (R + r) * sin(t) - d * sin(ratio * t): epicycloid depth coordinate.
        val z = (majorRadius + minorRadius) * sin(t) - distance * sin(ratio * t)
        return Vector(x, 0.0, z)
    }

    // converts spherical coordinates to a vector
    // https://en.wikipedia.org/wiki/Spherical_coordinate_system x = r sin(phi) cos(theta)
    fun spherical(radius: Double, phi: Double, theta: Double): Vector {
        // x = r * sin(phi) * cos(theta): horizontal projection onto X
        val x = radius * sin(phi) * cos(theta)
        // y = r * cos(phi): vertical coordinate from polar angle
        val y = radius * cos(phi)
        // z = r * sin(phi) * sin(theta): horizontal projection onto Z
        val z = radius * sin(phi) * sin(theta)
        return Vector(x, y, z)
    }

    // returns uniformly distributed unit sphere points using the Fibonacci method
    // y = 1 - (i / (n - 1)) * 2 and theta = goldenAngle * i
    fun fibonacciSphere(samples: Int): List<Vector> {
        if (samples <= 1) {
            return listOf(Vector(0.0, 1.0, 0.0))
        }
        return List(samples) { index ->
            // y = 1 - (i / (n - 1)) * 2: distributes points evenly from top to bottom
            val y = 1.0 - (index / (samples - 1.0)) * 2.0
            // r = sqrt(1 - y * y): radius of the horizontal slice at this height
            val sliceRadius = sqrt(max(0.0, 1.0 - y * y))
            // theta = goldenAngle * i: rotates each point by an irrational angle to avoid bands
            val theta = GOLDEN_ANGLE * index
            // x = sliceRadius * cos(theta): X coordinate on the slice
            val x = sliceRadius * cos(theta)
            // z = sliceRadius * sin(theta): Z coordinate on the slice
            val z = sliceRadius * sin(theta)
            Vector(x, y, z)
        }
    }

    // rotates a vector around an arbitrary axis using Rodrigues' formula
    // https://en.wikipedia.org/wiki/Rodrigues%27_rotation_formula v cos(theta) + (k x v) sin(theta) + k(k dot v)(1 - cos(theta))
    fun rotateRodrigues(vector: Vector, axis: Vector, theta: Double): Vector {
        val k = axis.clone().normalize()
        // v * cos(theta): keeps the component aligned with the original vector
        val parallel = vector.clone().multiply(cos(theta))
        // (k x v) * sin(theta): adds the perpendicular swept component
        val perpendicular = k.clone().crossProduct(vector).multiply(sin(theta))
        // k * (k dot v) * (1 - cos(theta)): preserves the component projected onto the axis
        val axial = k.clone().multiply(k.dot(vector) * (1.0 - cos(theta)))
        return parallel.add(perpendicular).add(axial)
    }

    // Calculates a cubic Bezier point
    // https://en.wikipedia.org/wiki/B%C3%A9zier_curve B(t) = (1 - t)^3 P0 + 3(1 - t)^2 t P1 + 3(1 - t)t^2 P2 + t^3 P3
    fun bezier(p0: Location, p1: Location, p2: Location, p3: Location, t: Double): Location {
        // u = 1 - t: inverse interpolation parameter
        val u = 1.0 - t
        // x = u^3*p0 + 3*u^2*t*p1 + 3*u*t^2*p2 + t^3*p3: cubic Bezier X coordinate
        val x = u.pow(3.0) * p0.x + 3.0 * u.pow(2.0) * t * p1.x + 3.0 * u * t.pow(2.0) * p2.x + t.pow(3.0) * p3.x
        // y = u^3*p0 + 3*u^2*t*p1 + 3*u*t^2*p2 + t^3*p3: cubic Bezier Y coordinate
        val y = u.pow(3.0) * p0.y + 3.0 * u.pow(2.0) * t * p1.y + 3.0 * u * t.pow(2.0) * p2.y + t.pow(3.0) * p3.y
        // z = u^3*p0 + 3*u^2*t*p1 + 3*u*t^2*p2 + t^3*p3: cubic Bezier Z coordinate
        val z = u.pow(3.0) * p0.z + 3.0 * u.pow(2.0) * t * p1.z + 3.0 * u * t.pow(2.0) * p2.z + t.pow(3.0) * p3.z
        return Location(p0.world, x, y, z, p0.yaw, p0.pitch)
    }

    // calculates deterministic fractional Brownian motion from value noise
    // val += amp * noise(x * freq, y * freq, z * freq); amp *= 0.5; freq *= 2.0
    fun fbm(x: Double, y: Double, z: Double, octaves: Int): Double {
        var value = 0.0
        var amplitude = HALF
        var frequency = 1.0
        repeat(octaves.coerceAtLeast(0)) {
            // val += amp * noise(position * freq): accumulates one octave of coherent value noise
            value += amplitude * valueNoise(x * frequency, y * frequency, z * frequency)
            // amp *= 0.5: halves contribution for the next octave
            amplitude *= HALF
            // freq *= 2.0: doubles detail frequency for the next octave
            frequency *= 2.0
        }
        return value
    }

    // calculates linear drag acceleration from velocity
    fun linearDrag(velocity: Vector, coefficient: Double): Vector {
        // a_drag = -c * v: opposes current velocity with a force proportional to speed
        return velocity.clone().multiply(-coefficient)
    }

    // calculates a divergence-free curl-noise vector from an fBm vector potential
    // formula: `curl(F) = (dFz/dy - dFy/dz, dFx/dz - dFz/dx, dFy/dx - dFx/dy)
    fun curlNoise(x: Double, y: Double, z: Double, time: Double, frequency: Double, octaves: Int, epsilon: Double): Vector {
        // safeFrequency = max(frequency, minFrequency): prevents mirrored or invalid negative noise scaling
        val safeFrequency = max(frequency, MIN_CURL_FREQUENCY)
        // px = x * frequency: scales world X into noise-space X
        val px = x * safeFrequency
        // py = y * frequency: scales world Y into noise-space Y
        val py = y * safeFrequency
        // pz = z * frequency: scales world Z into noise-space Z
        val pz = z * safeFrequency
        // e = max(epsilon, minEpsilon): keeps finite differences numerically stable
        val e = max(epsilon, MIN_CURL_EPSILON)
        // inv2e = 1 / (2e): central-difference normalization factor
        val inv2e = 1.0 / (2.0 * e)

        // dFz/dy = (Fz(y + e) - Fz(y - e)) / (2e): central derivative of vector potential Z by Y
        val dFzDy = (potentialZ(px, py + e, pz, time, octaves) - potentialZ(px, py - e, pz, time, octaves)) * inv2e
        // dFy/dz = (Fy(z + e) - Fy(z - e)) / (2e): central derivative of vector potential Y by Z
        val dFyDz = (potentialY(px, py, pz + e, time, octaves) - potentialY(px, py, pz - e, time, octaves)) * inv2e
        // dFx/dz = (Fx(z + e) - Fx(z - e)) / (2e): central derivative of vector potential X by Z
        val dFxDz = (potentialX(px, py, pz + e, time, octaves) - potentialX(px, py, pz - e, time, octaves)) * inv2e
        // dFz/dx = (Fz(x + e) - Fz(x - e)) / (2e): central derivative of vector potential Z by X
        val dFzDx = (potentialZ(px + e, py, pz, time, octaves) - potentialZ(px - e, py, pz, time, octaves)) * inv2e
        // dFy/dx = (Fy(x + e) - Fy(x - e)) / (2e): central derivative of vector potential Y by X
        val dFyDx = (potentialY(px + e, py, pz, time, octaves) - potentialY(px - e, py, pz, time, octaves)) * inv2e
        // dFx/dy = (Fx(y + e) - Fx(y - e)) / (2e): central derivative of vector potential X by Y
        val dFxDy = (potentialX(px, py + e, pz, time, octaves) - potentialX(px, py - e, pz, time, octaves)) * inv2e

        // worldDerivativeScale = frequency: converts noise-space derivatives back to world-space derivatives by the chain rule
        val worldDerivativeScale = safeFrequency
        // curlX = (dFz/dy - dFy/dz) * frequency: X component of ∇ × F in world-space
        val curlX = (dFzDy - dFyDz) * worldDerivativeScale
        // curlY = (dFx/dz - dFz/dx) * frequency: Y component of ∇ × F in world-space
        val curlY = (dFxDz - dFzDx) * worldDerivativeScale
        // curlZ = (dFy/dx - dFx/dy) * frequency: Z component of ∇ × F in world-space
        val curlZ = (dFyDx - dFxDy) * worldDerivativeScale
        return Vector(curlX, curlY, curlZ)
    }

    // applies scale, Y rotation, arbitrary tilt, and center translation
    fun transform(local: Vector, center: Location, scale: Double, rotationYaw: Double, tiltAxis: Vector?, tiltAngle: Double): Location {
        val transformed = local.clone().multiply(scale)
        if (rotationYaw != 0.0) {
            rotateYInPlace(transformed, rotationYaw)
        }
        if (tiltAxis != null && tiltAngle != 0.0 && tiltAxis.lengthSquared() > 0.0) {
            val tilted = rotateRodrigues(transformed, tiltAxis, tiltAngle)
            transformed.x = tilted.x
            transformed.y = tilted.y
            transformed.z = tilted.z
        }
        return center.clone().add(transformed)
    }

    // non-allocating transform: writes scale, Y rotation, arbitrary tilt, and center translation directly into output
    // used by the pooled render buffer so each frame allocates no per-particle Location
    fun transformInto(output: Location, local: Vector, center: Location, scale: Double, rotationYaw: Double, tiltAxis: Vector?, tiltAngle: Double) {
        transformInto(output, local.x, local.y, local.z, center, scale, rotationYaw, tiltAxis, tiltAngle)
    }

    // component-based non-allocating transform; avoids constructing an intermediate Vector for each particle
    fun transformInto(output: Location, x: Double, y: Double, z: Double, center: Location, scale: Double, rotationYaw: Double, tiltAxis: Vector?, tiltAngle: Double) {
        // scaled = local * scale: applied before rotation
        var sx = x * scale
        var sy = y * scale
        var sz = z * scale
        if (rotationYaw != 0.0) {
            val cosY = cos(rotationYaw)
            val sinY = sin(rotationYaw)
            // x' = x * cos - z * sin, z' = x * sin + z * cos: Y-axis rotation in the XZ plane
            val originalX = sx
            val originalZ = sz
            sx = originalX * cosY - originalZ * sinY
            sz = originalX * sinY + originalZ * cosY
        }
        if (tiltAxis != null && tiltAngle != 0.0 && tiltAxis.lengthSquared() > 0.0) {
            val length = tiltAxis.length()
            val axisX = tiltAxis.x / length
            val axisY = tiltAxis.y / length
            val axisZ = tiltAxis.z / length
            // dot = k dot v: component of v projected onto the normalized axis
            val dot = axisX * sx + axisY * sy + axisZ * sz
            val cosT = cos(tiltAngle)
            val sinT = sin(tiltAngle)
            // cross = k x v: perpendicular swept component
            val crossX = axisY * sz - axisZ * sy
            val crossY = axisZ * sx - axisX * sz
            val crossZ = axisX * sy - axisY * sx
            // oneMinusCos = 1 - cos(theta): axial preservation factor
            val oneMinusCos = 1.0 - cosT
            // v cos(theta) + (k x v) sin(theta) + k(k dot v)(1 - cos(theta)): Rodrigues rotation
            sx = sx * cosT + crossX * sinT + axisX * dot * oneMinusCos
            sy = sy * cosT + crossY * sinT + axisY * dot * oneMinusCos
            sz = sz * cosT + crossZ * sinT + axisZ * dot * oneMinusCos
        }
        // p = center + rotated: translates the local point into world coordinates
        output.x = center.x + sx
        output.y = center.y + sy
        output.z = center.z + sz
    }

    // non-allocating Rodrigues rotation plus origin translation
    // p' = origin + Rodrigues(v, axis, theta); writes the result into output
    fun rotateAndTranslateInto(output: Location, originX: Double, originY: Double, originZ: Double, vx: Double, vy: Double, vz: Double, axis: Vector, theta: Double) {
        val length = axis.length()
        if (length <= MIN_CURL_EPSILON) {
            output.x = originX + vx
            output.y = originY + vy
            output.z = originZ + vz
            return
        }
        val axisX = axis.x / length
        val axisY = axis.y / length
        val axisZ = axis.z / length
        // dot = k dot v: component of v projected onto the normalized axis
        val dot = axisX * vx + axisY * vy + axisZ * vz
        val cosT = cos(theta)
        val sinT = sin(theta)
        // cross = k x v: perpendicular swept component
        val crossX = axisY * vz - axisZ * vy
        val crossY = axisZ * vx - axisX * vz
        val crossZ = axisX * vy - axisY * vx
        // oneMinusCos = 1 - cos(theta): axial preservation factor
        val oneMinusCos = 1.0 - cosT
        // origin + v cos(theta) + (k x v) sin(theta) + k(k dot v)(1 - cos(theta)): Rodrigues rotation
        output.x = originX + vx * cosT + crossX * sinT + axisX * dot * oneMinusCos
        output.y = originY + vy * cosT + crossY * sinT + axisY * dot * oneMinusCos
        output.z = originZ + vz * cosT + crossZ * sinT + axisZ * dot * oneMinusCos
    }

    // calculates a normalized Gaussian density weight
    // e^(-(x - mu)^2 / (2 sigma^2))
    fun gaussian(x: Double, mean: Double, sigma: Double): Double {
        // exponent = -((x - mean)^2) / (2 * sigma^2): Gaussian falloff from the mean
        val exponent = -((x - mean).pow(2.0)) / (2.0 * sigma.pow(2.0))
        return kotlin.math.exp(exponent)
    }

    private fun rotateYInPlace(vector: Vector, theta: Double) {
        val originalX = vector.x
        val originalZ = vector.z
        // x' = x * cos(theta) - z * sin(theta): Y-axis rotation in the XZ plane
        vector.x = originalX * cos(theta) - originalZ * sin(theta)
        // z' = x * sin(theta) + z * cos(theta): Y-axis rotation in the XZ plane
        vector.z = originalX * sin(theta) + originalZ * cos(theta)
    }

    private fun valueNoise(x: Double, y: Double, z: Double): Double {
        val xi = floor(x)
        val yi = floor(y)
        val zi = floor(z)
        val xf = x - xi
        val yf = y - yi
        val zf = z - zi
        val u = smoothstep(xf)
        val v = smoothstep(yf)
        val w = smoothstep(zf)
        val x00 = lerp(hash(xi, yi, zi), hash(xi + 1.0, yi, zi), u)
        val x10 = lerp(hash(xi, yi + 1.0, zi), hash(xi + 1.0, yi + 1.0, zi), u)
        val x01 = lerp(hash(xi, yi, zi + 1.0), hash(xi + 1.0, yi, zi + 1.0), u)
        val x11 = lerp(hash(xi, yi + 1.0, zi + 1.0), hash(xi + 1.0, yi + 1.0, zi + 1.0), u)
        val y0 = lerp(x00, x10, v)
        val y1 = lerp(x01, x11, v)
        return lerp(y0, y1, w)
    }

    private fun potentialX(x: Double, y: Double, z: Double, time: Double, octaves: Int): Double {
        // animatedZ = z + time * speed: advects the X potential slowly through noise-space
        val animatedZ = z + time * CURL_TIME_SPEED
        // Fx = fbm(x + offsetX, y, animatedZ): decorrelates the X component of the vector potential
        return fbm(x + CURL_POTENTIAL_X_OFFSET, y, animatedZ, octaves)
    }

    private fun potentialY(x: Double, y: Double, z: Double, time: Double, octaves: Int): Double {
        // animatedX = x + time * speed: advects the Y potential slowly through noise-space
        val animatedX = x + time * CURL_TIME_SPEED
        // Fy = fbm(animatedX, y + offsetY, z): decorrelates the Y component of the vector potential
        return fbm(animatedX, y + CURL_POTENTIAL_Y_OFFSET, z, octaves)
    }

    private fun potentialZ(x: Double, y: Double, z: Double, time: Double, octaves: Int): Double {
        // animatedY = y + time * speed: advects the Z potential slowly through noise-space
        val animatedY = y + time * CURL_TIME_SPEED
        // Fz = fbm(x, animatedY, z + offsetZ): decorrelates the Z component of the vector potential
        return fbm(x, animatedY, z + CURL_POTENTIAL_Z_OFFSET, octaves)
    }

    private fun smoothstep(t: Double): Double = t * t * (3.0 - 2.0 * t)

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun hash(x: Double, y: Double, z: Double): Double {
        val n = sin(x * HASH_X + y * HASH_Y + z * HASH_Z) * HASH_SCALE
        return (n - floor(n)) * 2.0 - 1.0
    }
}
