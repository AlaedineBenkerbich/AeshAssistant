package fr.alaedine.aesh.domain.model

/**
 * Free-text observation fields extracted from dictated speech and/or photos
 * of handwritten notes, ready to be merged into the daily observation form.
 *
 * @property sortedByAi `true` when the on-device AI split the text across
 * the fields; `false` when it couldn't (unavailable, failed, or input too
 * long) and everything was placed in [freeNotes] as-is so nothing is lost.
 */
data class ExtractedObservationNotes(
    val obstacles: String = "",
    val supportStrategies: String = "",
    val freeNotes: String = "",
    val sortedByAi: Boolean = true,
)
