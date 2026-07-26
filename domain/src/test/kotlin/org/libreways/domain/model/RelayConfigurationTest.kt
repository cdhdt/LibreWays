package org.libreways.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayConfigurationTest {
    @Test
    fun `NotChosen is not a member of the selectable modes`() {
        val notChosen: RelayConfiguration = RelayConfiguration.NotChosen

        assertFalse(notChosen is RelayConfiguration.Selected)
    }

    @Test
    fun `Direct is a member of the selectable modes`() {
        val direct: RelayConfiguration = RelayConfiguration.Direct

        assertTrue(direct is RelayConfiguration.Selected)
    }
}
