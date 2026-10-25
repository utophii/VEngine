package com.utophii.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// reference values from Abramowitz & Stegun, tables 9.1 and 9.5
class BesselTest {

    @Test
    fun `values match the tables`() {
        assertEquals(0.7651976865579666, Bessel.j(0, 1.0), EPS)
        assertEquals(0.5767248077568734, Bessel.j(1, 2.0), EPS)
        assertEquals(-0.2340615281867936, Bessel.j(5, 10.0), EPS)
        assertEquals(-Bessel.j(3, 4.0), Bessel.j(-3, 4.0), EPS)
    }

    @Test
    fun `zeros match the tables`() {
        assertEquals(2.404825557695773, Bessel.zero(0, 1), ROOT_EPS)
        assertEquals(5.520078110286311, Bessel.zero(0, 2), ROOT_EPS)
        assertEquals(3.831705970207512, Bessel.zero(1, 1), ROOT_EPS)
        assertEquals(11.61984117214906, Bessel.zero(2, 3), ROOT_EPS)
    }

    @Test
    fun `extrema are the zeros of the derivative`() {
        // J₁' first vanishes at 1.8412, and J₀' = −J₁ vanishes at the zeros of J₁
        assertEquals(1.841183781340659, Bessel.extremum(1, 0.0, Bessel.zero(1, 1)), ROOT_EPS)
        assertEquals(3.831705970207512, Bessel.extremum(0, Bessel.zero(0, 1), Bessel.zero(0, 2)), ROOT_EPS)
    }

    companion object {
        private const val EPS = 1e-12
        private const val ROOT_EPS = 1e-7
    }
}
