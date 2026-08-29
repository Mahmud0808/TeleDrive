package com.drdisagree.teledrive.domain.model

/** A dialling destination, as Telegram itself lists them. */
data class Country(
    val isoCode: String,
    val name: String,
    val callingCode: String
) {

    /** Flag drawn from the regional indicators for the ISO code. */
    val flag: String
        get() = isoCode
            .uppercase()
            .filter { it in 'A'..'Z' }
            .takeIf { it.length == 2 }
            ?.map { Character.toChars(REGIONAL_INDICATOR_BASE + (it - 'A')).concatToString() }
            ?.joinToString("")
            .orEmpty()

    private companion object {
        const val REGIONAL_INDICATOR_BASE = 0x1F1E6
    }
}
