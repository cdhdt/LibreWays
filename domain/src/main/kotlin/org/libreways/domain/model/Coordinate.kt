package org.libreways.domain.model

/**
 * A latitude/longitude pair, the shared geographic value type every other domain entity built on
 * top of a location uses (docs/architecture/README.md §2.1). Constructing a [Coordinate] outside
 * the valid geographic range is a contract violation, not an expected runtime failure, so it is
 * reported as an [IllegalArgumentException] rather than a domain result type.
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in MIN_LATITUDE..MAX_LATITUDE) {
            "latitude must be in [$MIN_LATITUDE, $MAX_LATITUDE], was $latitude"
        }
        require(longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "longitude must be in [$MIN_LONGITUDE, $MAX_LONGITUDE], was $longitude"
        }
    }

    private companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
