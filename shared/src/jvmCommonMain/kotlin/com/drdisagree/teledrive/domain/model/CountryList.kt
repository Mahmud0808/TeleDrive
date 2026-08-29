package com.drdisagree.teledrive.domain.model

/** Countries to offer, and the one to preselect. */
data class CountryList(
    val countries: List<Country>,
    val detected: Country?
)
