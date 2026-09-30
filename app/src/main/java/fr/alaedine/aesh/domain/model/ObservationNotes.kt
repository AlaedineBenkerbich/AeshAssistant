package fr.alaedine.aesh.domain.model

/**
 * The three free-text fields of a daily observation (see [DailyReport]),
 * as sorted out of notes that were dictated or photographed instead of
 * typed into the form field by field.
 *
 * @property obstacles Difficulties the student ran into
 * ([DailyReport.obstacles]).
 * @property supportStrategies Support or strategies that helped the student
 * ([DailyReport.supportStrategies]).
 * @property freeNotes Everything else worth remembering
 * ([DailyReport.freeNotes]).
 */
data class ObservationNotes(
    val obstacles: String = "",
    val supportStrategies: String = "",
    val freeNotes: String = "",
) {
    companion object {
        /**
         * Keeps [rawNotes] as they are in [freeNotes], for when they couldn't
         * be sorted into the other fields: the user's words must never be
         * lost just because the sorting step wasn't possible.
         */
        fun unsorted(rawNotes: String): ObservationNotes = ObservationNotes(freeNotes = rawNotes.trim())
    }
}
