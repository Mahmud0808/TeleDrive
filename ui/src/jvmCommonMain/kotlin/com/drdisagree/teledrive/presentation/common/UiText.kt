package com.drdisagree.teledrive.presentation.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Text a view model hands to the UI without resolving it, so the screen picks
 * the current locale at display time and view models stay platform-free.
 */
sealed interface UiText {

    data class Plain(val value: String) : UiText

    class Resource(val resource: StringResource, vararg val args: Any) : UiText

    class PluralResource(
        val resource: PluralStringResource,
        val quantity: Int,
        vararg val args: Any
    ) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> stringResource(resource, *args)
    is UiText.PluralResource -> pluralStringResource(resource, quantity, *args)
}

suspend fun UiText.load(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> getString(resource, *args)
    is UiText.PluralResource -> getPluralString(resource, quantity, *args)
}
