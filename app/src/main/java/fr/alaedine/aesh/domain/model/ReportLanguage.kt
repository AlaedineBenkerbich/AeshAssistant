package fr.alaedine.aesh.domain.model

/**
 * The language an AI-generated ESS report is written in. The prompt is
 * written in that language too, and asks for it explicitly: a small
 * on-device model tends to answer in the language of its instructions.
 */
enum class ReportLanguage {
    French,
    English,
    ;

    companion object {
        /**
         * French for a French [languageCode] (ISO 639, e.g. `"fr"`), English otherwise, matching the app's two
         * translations: anyone who doesn't see the app in French sees it in English.
         */
        fun fromLanguageCode(languageCode: String): ReportLanguage = if (languageCode.equals("fr", ignoreCase = true)) French else English
    }
}
