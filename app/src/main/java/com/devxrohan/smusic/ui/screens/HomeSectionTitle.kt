package com.devxrohan.smusic.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.devxrohan.smusic.ui.component.NavigationTitle
import java.util.Locale

private val headingWordStart = Regex("(^|\\s)(\\p{Ll})")

/** Home section copy follows one case style without lowercasing acronyms or metadata. */
internal fun homeHeadingCase(text: String, locale: Locale): String =
    headingWordStart.replace(text) { match ->
        match.groupValues[1] + match.groupValues[2].uppercase(locale)
    }

@Composable
internal fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    preserveTitle: Boolean = false,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    NavigationTitle(
        title = if (preserveTitle) title else homeHeadingCase(title, locale),
        label = label?.let { homeHeadingCase(it, locale) },
        modifier = modifier,
        thumbnail = thumbnail,
        onClick = onClick,
    )
}
