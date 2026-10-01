package com.drdisagree.teledrive.domain.model

data class CountryList(
    val countries: List<Country>,
    val detected: Country?
)
