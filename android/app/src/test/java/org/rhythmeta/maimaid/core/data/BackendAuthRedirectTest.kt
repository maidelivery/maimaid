package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendAuthRedirectTest {
    @Test
    fun parsesSessionCallbackQuery() {
        val redirect = BackendAuthRedirect.parse(
            "maimaid://auth/callback?type=session&result=success&sessionCode=abc%2B123",
        )

        assertEquals("session", redirect?.type)
        assertEquals("success", redirect?.result)
        assertEquals("abc+123", redirect?.sessionCode)
    }

    @Test
    fun parsesVerificationCallbackFragment() {
        val redirect = BackendAuthRedirect.parse(
            "maimaid://auth/callback#result=error&code=invalid_verification_token",
        )

        assertEquals("error", redirect?.result)
        assertEquals("invalid_verification_token", redirect?.code)
    }

    @Test
    fun rejectsForeignCallback() {
        assertNull(BackendAuthRedirect.parse("other://auth/callback?result=success"))
    }
}
