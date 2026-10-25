package com.utophii.math

// stateless deterministic hashing
object Hash {
    private const val GOLDEN_GAMMA = -0x61c8864680b583ebL
    private const val MIX_1 = -0x40a7b892e31b1a47L
    private const val MIX_2 = -0x6b2fb644ecceee15L
    private const val DOUBLE_UNIT = 1.0 / (1L shl 53)

    // splitmix64 finalizer: spreads consecutive seeds over unrelated 64-bit values
    fun mix(seed: Long): Long {
        var z = seed * GOLDEN_GAMMA + GOLDEN_GAMMA
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    // uniform in [-1, 1) from the top 53 bits of a hash
    fun signedUnit(hash: Long): Double = (hash ushr 11) * DOUBLE_UNIT * 2.0 - 1.0
}
