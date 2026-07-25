package org.libreways.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CoordinateTest {
    @Test
    fun `rejects a latitude outside -90 to 90`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinate(latitude = 90.1, longitude = 0.0)
        }
    }

    @Test
    fun `rejects a longitude outside -180 to 180`() {
        assertFailsWith<IllegalArgumentException> {
            Coordinate(latitude = 0.0, longitude = 180.1)
        }
    }
}
