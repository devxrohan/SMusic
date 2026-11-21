package com.devxrohan.smusic.spotify

/** Unknown ownership never permits removing an existing service like. */
internal object SpotifyMirrorPolicy {
    fun completeListing(totalKnown: Boolean, allRowsParsed: Boolean, ids: Set<String>, total: Int): Boolean =
        totalKnown && allRowsParsed && ids.none { it.isBlank() } && ids.size == total

    enum class Action { ACKNOWLEDGE, ADD_OWNED, ADD_PROTECTED, REMOVE_OWNED }
    fun action(liked: Boolean, alreadySent: Boolean, likedThere: Boolean?, owned: Boolean): Action = when {
        alreadySent -> Action.ACKNOWLEDGE
        !liked -> if (owned) Action.REMOVE_OWNED else Action.ACKNOWLEDGE
        likedThere == true && !owned -> Action.ACKNOWLEDGE
        likedThere == false -> Action.ADD_OWNED
        else -> Action.ADD_PROTECTED
    }
}
