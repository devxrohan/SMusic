package com.devxrohan.smusic.spotify

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val SpotifySpDcKey = stringPreferencesKey("spotify_sp_dc")
val SpotifySpKeyKey = stringPreferencesKey("spotify_sp_key")
val SpotifyAccessTokenKey = stringPreferencesKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longPreferencesKey("spotify_access_token_expires_at")
val SpotifyAccountNameKey = stringPreferencesKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringPreferencesKey("spotify_account_avatar_url")
val SpotifyLibraryPlaylistsCacheKey = stringPreferencesKey("spotify_library_playlists_cache")
val ShowSpotifyPlaylistsKey = booleanPreferencesKey("showSpotifyPlaylists")

val SpotifyBlockedItemsKey = androidx.datastore.preferences.core.stringSetPreferencesKey("spotify_blocked_sync_items")
val SpotifyImportedLinksKey = stringPreferencesKey("spotify_imported_library_links")

val SMusicLikedIdsKey = androidx.datastore.preferences.core.stringSetPreferencesKey("smusic_explicit_liked_song_ids")

val SpotifyPendingLikesKey = stringPreferencesKey("spotify_pending_app_likes")
val SpotifyExportedPlaylistsKey = stringPreferencesKey("spotify_exported_local_playlists")
val SpotifyExportedSavedKey = stringPreferencesKey("spotify_exported_saved_items")
val SpotifyOutboundMatchesKey = stringPreferencesKey("spotify_outbound_track_matches")
val SpotifyExportExclusionsKey = androidx.datastore.preferences.core.stringSetPreferencesKey("spotify_export_exclusions")

val SpotifySentLikesKey = stringPreferencesKey("spotify_sent_like_states")
val SpotifySavedIntentKey = stringPreferencesKey("spotify_saved_item_intent")
val SpotifySentSavedKey = stringPreferencesKey("spotify_sent_saved_states")
val SpotifyExportAccountKey = stringPreferencesKey("spotify_export_account_id")

val SpotifyClientIdKey = stringPreferencesKey("spotify_web_client_id")
val SpotifyLikedCatalogIdsKey = androidx.datastore.preferences.core.stringSetPreferencesKey("spotify_liked_catalog_ids")
val SpotifyLikedCatalogCompleteAtKey = androidx.datastore.preferences.core.longPreferencesKey("spotify_liked_catalog_complete_at")
val SpotifyOwnedLikesKey = androidx.datastore.preferences.core.stringSetPreferencesKey("spotify_app_owned_likes")

val SpotifyLikedCatalogFetchedAtKey = androidx.datastore.preferences.core.longPreferencesKey("spotify_liked_catalog_fetched_at")

val SpotifyPendingPlaylistAddsKey = stringPreferencesKey("spotify_pending_playlist_additions")
