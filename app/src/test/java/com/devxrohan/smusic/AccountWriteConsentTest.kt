package com.devxrohan.smusic

import com.devxrohan.smusic.utils.AccountWriteConsent
import org.junit.Assert.*
import org.junit.Test

class AccountWriteConsentTest {
    @Test fun approvalIsBoundToTheProviderAndAccountCredential() {
        val youtube = AccountWriteConsent.fingerprint(false, "account-a")
        assertNotEquals(youtube, AccountWriteConsent.fingerprint(false, "account-b"))
        assertNotEquals(youtube, AccountWriteConsent.fingerprint(true, "account-a"))
        assertEquals(youtube, AccountWriteConsent.fingerprint(false, "account-a"))
    }
}
