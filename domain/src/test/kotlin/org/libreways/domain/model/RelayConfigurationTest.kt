package org.libreways.domain.model

import kotlin.test.Test
import kotlin.test.assertNotEquals

class RelayConfigurationTest {
    @Test
    fun `NotChosen is distinct from every selectable mode`() {
        val notChosen: RelayConfiguration = RelayConfiguration.NotChosen

        val proxy = RelayConfiguration.Proxy(host = "proxy.example", port = 1080)
        val selfHosted = RelayConfiguration.SelfHosted(endpointUrl = "https://relay.example")

        assertNotEquals<RelayConfiguration>(RelayConfiguration.Direct, notChosen)
        assertNotEquals<RelayConfiguration>(RelayConfiguration.Tor, notChosen)
        assertNotEquals<RelayConfiguration>(proxy, notChosen)
        assertNotEquals<RelayConfiguration>(selfHosted, notChosen)
    }
}
