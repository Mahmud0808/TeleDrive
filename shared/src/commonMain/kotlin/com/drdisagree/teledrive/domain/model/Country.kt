package com.drdisagree.teledrive.domain.model

data class Country(
    val isoCode: String,
    val name: String,
    val callingCode: String
) {

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
