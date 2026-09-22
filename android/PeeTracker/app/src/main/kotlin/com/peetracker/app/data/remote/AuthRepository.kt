package com.peetracker.app.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val userRepository: UserRepository,
    @ApplicationContext private val appContext: Context
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    // Credential Manager replaces the deprecated GoogleSignInClient/GoogleSignInOptions API,
    // which Google has marked legacy in favor of this unified sign-in surface.
    suspend fun signInWithGoogleCredential(serverClientId: String): FirebaseUser {
        val credentialManager = CredentialManager.create(appContext)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(appContext, request)
        val credential = result.credential

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Unexpected credential type returned by Credential Manager")
        }

        val googleIdTokenCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            error("Failed to parse Google ID token: ${e.message}")
        }

        val firebaseCredential: AuthCredential =
            GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)

        val user = auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase sign-in returned no user")
        // Google sign-in covers both new and returning users through the same call, so the
        // "doc doesn't exist yet" check inside createProfileIfMissing is what decides whether
        // this actually creates anything.
        userRepository.createProfileIfMissing(
            uid = user.uid,
            displayName = user.displayName.orEmpty(),
            photoURL = user.photoUrl?.toString(),
            authProviders = listOf("google")
        )
        return user
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        return auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase sign-in returned no user")
    }

    suspend fun signUpWithEmail(email: String, password: String): FirebaseUser {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: error("Firebase sign-up returned no user")
        userRepository.createProfileIfMissing(
            uid = user.uid,
            displayName = user.displayName.orEmpty(),
            photoURL = user.photoUrl?.toString(),
            authProviders = listOf("password")
        )
        return user
    }

    fun signOut() {
        auth.signOut()
    }
}
