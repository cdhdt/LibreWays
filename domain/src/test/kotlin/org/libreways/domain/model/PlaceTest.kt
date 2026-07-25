package org.libreways.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class PlaceTest {
    @Test
    fun `rejects a blank label`() {
        assertFailsWith<IllegalArgumentException> {
            Place(label = "   ", coordinate = Coordinate(latitude = 0.0, longitude = 0.0))
        }
    }

    @Test
    fun `requires a valid Coordinate`() {
        assertFailsWith<IllegalArgumentException> {
            Place(label = "Home", coordinate = Coordinate(latitude = 90.1, longitude = 0.0))
        }
    }
}
