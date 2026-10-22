package com.trackme.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GoogleUserData(
    val idToken: String,
    val email: String,
    val displayName: String?,
    val profilePictureUri: String?
)

object GoogleAuthManager {
    // Configured directly with your Firebase Web Client ID
    private const val WEB_CLIENT_ID = "757771017613-leg77sv2a0936n64afcnlh855jmd8qmr.apps.googleusercontent.com"

    suspend fun signInWithGoogle(context: Context): Result<GoogleUserData> = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        try {
            val response = credentialManager.getCredential(
                request = request,
                context = context
            )
            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val user = GoogleUserData(
                    idToken = googleIdTokenCredential.idToken,
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()
                )
                Result.success(user)
            } else {
                Result.failure(Exception("Unsupported credential type returned"))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
