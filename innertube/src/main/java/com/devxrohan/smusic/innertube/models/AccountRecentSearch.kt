package com.devxrohan.smusic.innertube.models

/** A verified YouTube Music account search-history row and its server-issued removal token. */
data class AccountRecentSearch(
    val query: String,
    val feedbackToken: String,
)
