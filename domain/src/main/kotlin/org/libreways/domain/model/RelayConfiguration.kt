package org.libreways.domain.model

/**
 * The user's relay choice for every outbound request this app makes (CLAUDE.md §5.1, decision D1).
 *
 * [NotChosen] is a distinct, egress-blocking state — never equivalent to [Direct], and never the
 * implicit default before a choice is made. Collapsing "no choice made yet" into "direct, no
 * relay" would silently defeat the fail-closed requirement FR-8 depends on, so the two are
 * separate types the compiler cannot confuse, not two values of one enum a reviewer has to keep
 * distinct by convention.
 */
sealed interface RelayConfiguration {
    /** No relay choice has been made yet. Blocks all egress; not a selectable mode. */
    data object NotChosen : RelayConfiguration

    /** Every mode the user can actively select, including "direct, no relay" itself. */
    sealed interface Selected : RelayConfiguration

    /** The user explicitly chose to make requests with no relay. */
    data object Direct : Selected

    /** The user chose to route requests through Tor. */
    data object Tor : Selected

    /** The user chose an HTTP or SOCKS proxy at [host]:[port]. */
    data class Proxy(
        val host: String,
        val port: Int,
    ) : Selected

    /** The user chose a self-hosted relay instance at [endpointUrl]. */
    data class SelfHosted(
        val endpointUrl: String,
    ) : Selected
}
