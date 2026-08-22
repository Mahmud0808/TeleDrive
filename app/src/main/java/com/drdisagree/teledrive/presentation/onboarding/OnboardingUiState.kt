package com.drdisagree.teledrive.presentation.onboarding

import com.drdisagree.teledrive.core.telegram.CodeDeliveryChannel
import com.drdisagree.teledrive.domain.model.Country
import com.drdisagree.teledrive.domain.model.DriveChannel

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val working: Boolean = false,
    val error: String? = null,
    val codePhoneNumber: String = "",
    val codeChannel: CodeDeliveryChannel = CodeDeliveryChannel.TELEGRAM_APP,
    val codeLength: Int? = null,
    val passwordHint: String? = null,
    val registrationRequired: Boolean = false,
    val qrLink: String? = null,
    val qrMode: Boolean = false,
    val countries: List<Country> = emptyList(),
    val countryLoadState: CountryLoadState = CountryLoadState.LOADING,
    val selectedCountry: Country? = null,
    val channels: List<DriveChannel> = emptyList(),
    val selectedChatId: Long? = null,
    val channelCreated: Boolean = false,
    val backupDcim: Boolean = true,
    val backupPictures: Boolean = true,
    val backupMovies: Boolean = false,
    val autoBackupEnabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val finishing: Boolean = false
)
