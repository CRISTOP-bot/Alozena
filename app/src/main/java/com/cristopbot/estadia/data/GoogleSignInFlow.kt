package com.cristopbot.estadia.data

import android.app.Activity
import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Credential Manager boundary: account selection and ID-token validation only. Firebase remains the identity provider. */
sealed interface GoogleSignInResult {
    data class Token(val idToken: String) : GoogleSignInResult
    data object NoAccounts : GoogleSignInResult
    data object Cancelled : GoogleSignInResult
    data object Failed : GoogleSignInResult
}

enum class CredentialFlowFailure { Cancelled, NoCredentials, Other }

fun shouldFallbackToAccountChooser(failure: CredentialFlowFailure): Boolean =
    failure == CredentialFlowFailure.Cancelled || failure == CredentialFlowFailure.NoCredentials

fun accountChooserResult(failure: CredentialFlowFailure): GoogleSignInResult = when (failure) {
    CredentialFlowFailure.Cancelled -> GoogleSignInResult.Cancelled
    CredentialFlowFailure.NoCredentials -> GoogleSignInResult.NoAccounts
    CredentialFlowFailure.Other -> GoogleSignInResult.Failed
}

class GoogleSignInFlow {
    suspend fun requestIdToken(activity: Activity, serverClientId: String): GoogleSignInResult {
        val manager = CredentialManager.create(activity)
        val directRequest = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()

        val response = try {
            manager.getCredential(activity, directRequest)
        } catch (_: GetCredentialCancellationException) {
            return if (shouldFallbackToAccountChooser(CredentialFlowFailure.Cancelled)) {
                requestAccountChooser(manager, activity, serverClientId)
            } else GoogleSignInResult.Failed
        } catch (_: NoCredentialException) {
            return if (shouldFallbackToAccountChooser(CredentialFlowFailure.NoCredentials)) {
                requestAccountChooser(manager, activity, serverClientId)
            } else GoogleSignInResult.Failed
        } catch (error: Exception) {
            logCredentialFailure("direct sign-in", error)
            return GoogleSignInResult.Failed
        }
        return tokenFrom(response.credential)
    }

    private suspend fun requestAccountChooser(
        manager: CredentialManager,
        activity: Activity,
        serverClientId: String
    ): GoogleSignInResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(false)
                    .build()
            )
            .build()
        val response = try {
            manager.getCredential(activity, request)
        } catch (_: GetCredentialCancellationException) {
            return accountChooserResult(CredentialFlowFailure.Cancelled)
        } catch (_: NoCredentialException) {
            return accountChooserResult(CredentialFlowFailure.NoCredentials)
        } catch (error: Exception) {
            logCredentialFailure("account chooser", error)
            return accountChooserResult(CredentialFlowFailure.Other)
        }
        return tokenFrom(response.credential)
    }

    private fun tokenFrom(credential: Credential): GoogleSignInResult {
        return try {
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Log.w(LOG_TAG, "Credential Manager returned an unsupported credential type")
                GoogleSignInResult.Failed
            } else {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
                    .takeIf(String::isNotBlank)
                    ?.let(GoogleSignInResult::Token)
                    ?: run {
                        Log.w(LOG_TAG, "Google returned an empty ID token")
                        GoogleSignInResult.Failed
                    }
            }
        } catch (error: Exception) {
            logCredentialFailure("ID token parsing", error)
            GoogleSignInResult.Failed
        }
    }

    private fun logCredentialFailure(stage: String, error: Exception) {
        Log.w(LOG_TAG, "$stage failed (${error.javaClass.simpleName})")
    }

    private companion object {
        const val LOG_TAG = "AlozenaAuth"
    }
}

/** Keep backend details out of the UI; codes are used only to select a plain-language message. */
fun firebaseSignInMessage(errorCode: String): String = when (errorCode) {
    "ERROR_OPERATION_NOT_ALLOWED" -> "El acceso con Google no está habilitado para Alozena. Revisa la configuración de Firebase o continúa como invitado."
    "ERROR_NETWORK_REQUEST_FAILED" -> "No pudimos conectarnos. Revisa tu conexión e inténtalo de nuevo."
    "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_IDP_RESPONSE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
        "No pudimos validar la cuenta de Google. Revisa el proveedor y el certificado SHA registrado en Firebase; también puedes continuar como invitado."
    else -> "No se pudo completar el acceso con Google. Inténtalo de nuevo o sigue explorando como invitado."
}
