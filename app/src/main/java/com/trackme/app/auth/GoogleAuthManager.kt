package com.trackme.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class GoogleUserData(
    val idToken: String,
    val email: String,
    val displayName: String?,
    val profilePictureUri: String?
)

object GoogleAuthManager {

    // Your Web Client ID from Firebase / Cloud Console
    private const val WEB_CLIENT_ID = "757771017613-leg77sv2a0936n64afcnlh855jmd8qmr.apps.googleusercontent.com"

    suspend fun signInWithGoogle(context: Context): Result<GoogleUserData> = withContext(Dispatchers.Main) {
        val credentialManager = CredentialManager.create(context)

        // Generate a hashed nonce (required by GoogleIdOption)
        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Show all Google accounts on device
            .setServerClientId(WEB_CLIENT_ID)
            .setNonce(hashedNonce)                // Required for fresh sign-ins
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
                Result.failure(Exception("Unsupported credential type"))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
