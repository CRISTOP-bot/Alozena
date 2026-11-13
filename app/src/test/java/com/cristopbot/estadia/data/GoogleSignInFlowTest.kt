package com.cristopbot.estadia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInFlowTest {
    @Test fun firebaseFailuresAreMappedToPlainSpanishWithoutTechnicalDetails() {
        assertEquals(
            "El acceso con Google no está disponible por ahora. Puedes explorar Alozena como invitado e intentar más tarde.",
            firebaseSignInMessage("ERROR_OPERATION_NOT_ALLOWED")
        )
        assertEquals(
            "No pudimos conectarnos. Revisa tu conexión e inténtalo de nuevo.",
            firebaseSignInMessage("ERROR_NETWORK_REQUEST_FAILED")
        )
        assertEquals(
            "No se pudo completar el acceso con Google. Inténtalo de nuevo o sigue explorando como invitado.",
            firebaseSignInMessage("UNRECOGNIZED_INTERNAL_CODE")
        )
    }

    @Test fun chooserFallbackIsLimitedToRecoverableCancelAndMissingCredentials() {
        assertTrue(shouldFallbackToAccountChooser(CredentialFlowFailure.Cancelled))
        assertTrue(shouldFallbackToAccountChooser(CredentialFlowFailure.NoCredentials))
        assertFalse(shouldFallbackToAccountChooser(CredentialFlowFailure.Other))
        assertEquals(GoogleSignInResult.Cancelled, accountChooserResult(CredentialFlowFailure.Cancelled))
        assertEquals(GoogleSignInResult.NoAccounts, accountChooserResult(CredentialFlowFailure.NoCredentials))
        assertEquals(GoogleSignInResult.Failed, accountChooserResult(CredentialFlowFailure.Other))
    }

    @Test fun credentialFailuresNeverExposeExceptionNamesOrCodes() {
        listOf("ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_IDP_RESPONSE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL")
            .forEach { code ->
                val message = firebaseSignInMessage(code)
                assertFalse(message.contains(code))
                assertFalse(message.contains("Exception"))
            }
    }
}
