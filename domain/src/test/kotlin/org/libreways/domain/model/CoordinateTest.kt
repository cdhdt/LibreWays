package org.libreways.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoordinateTest {
    @Test
    fun `accepts a latitude at the minimum boundary`() {
        val coordinate = Coordinate(latitude = -90.0, longitude = 0.0)

        assertEquals(-90.0, coordinate.latitude)
    }

    @Test
    fun `accepts a latitude at the maximum boundary`() {
        val coordinate = Coordinate(latitude = 90.0, longitude = 0.0)

        assertEquals(90.0, coordinate.latitude)
    }

    @Test
    fun `accepts a latitude within range`() {
        val coordinate = Coordinate(latitude = 45.0, longitude = 0.0)

        assertEquals(45.0, coordinate.latitude)
    }

    @Test
    fun `rejects a latitude below the minimum`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinate(latitude = -90.1, longitude = 0.0)
        }
    }

    @Test
    fun `rejects a latitude above the maximum`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinate(latitude = 90.1, longitude = 0.0)
        }
    }

    @Test
    fun `accepts a longitude at the minimum boundary`() {
        val coordinate = Coordinate(latitude = 0.0, longitude = -180.0)

        assertEquals(-180.0, coordinate.longitude)
    }

    @Test
    fun `accepts a longitude at the maximum boundary`() {
        val coordinate = Coordinate(latitude = 0.0, longitude = 180.0)

        assertEquals(180.0, coordinate.longitude)
    }

    @Test
    fun `accepts a longitude within range`() {
        val coordinate = Coordinate(latitude = 0.0, longitude = 120.0)

        assertEquals(120.0, coordinate.longitude)
    }

    @Test
    fun `rejects a longitude below the minimum`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinate(latitude = 0.0, longitude = -180.1)
        }
    }

    @Test
    fun `rejects a longitude above the maximum`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinate(latitude = 0.0, longitude = 180.1)
        }
    }
}
