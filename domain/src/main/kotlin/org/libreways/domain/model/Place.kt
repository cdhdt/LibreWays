package org.libreways.domain.model

/**
 * A resolved destination search result: a human-readable [label] paired with a [Coordinate].
 * `docs/architecture/README.md` §2.1 also names a confidence score and a provenance field as part
 * of this type's eventual shape; they are added when a test in a later TDD stage first requires
 * them (Stage B, `ResolveDestination`), not speculatively here.
 */
data class Place(
    val label: String,
    val coordinate: Coordinate,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }
}
