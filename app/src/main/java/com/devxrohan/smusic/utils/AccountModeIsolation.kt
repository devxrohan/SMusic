package com.devxrohan.smusic.utils

import kotlinx.coroutines.sync.Mutex

/** Shared lock for account-session and account-mode synchronization operations. */
internal object AccountSyncGate {
    val mutex = Mutex()
}
