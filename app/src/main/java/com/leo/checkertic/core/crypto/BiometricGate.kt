package com.leo.checkertic.core.crypto

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Thin wrapper over `androidx.biometric`'s [BiometricPrompt].
 *
 * The prompt is shown *without* a `CryptoObject`, deliberately. The vault key
 * is time-based (see [Vault]) and the platform forbids pairing a CryptoObject
 * with `DEVICE_CREDENTIAL` fallback — so a CryptoObject here would lock out
 * every user who has a PIN but no enrolled fingerprint. Instead, a successful
 * prompt opens the Keystore auth window, and [Vault.confirmUsable] then does a
 * real cipher init to prove it actually opened rather than trusting the
 * callback.
 */
object BiometricGate {

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Unavailable(val reason: String) : Result
        data class Failed(val message: String) : Result
    }

    private const val ALLOWED = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /**
     * Whether any usable authenticator is enrolled.
     *
     * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` is probed as well, because a
     * device with only a face unlock classified as weak still has a valid
     * device credential the user can fall back to.
     */
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val strong = manager.canAuthenticate(ALLOWED)
        if (strong == BiometricManager.BIOMETRIC_SUCCESS) return true
        return manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun unavailableReason(context: Context): String? =
        when (BiometricManager.from(context).canAuthenticate(ALLOWED)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "This device has no biometric hardware."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware is unavailable right now."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Set a screen lock or enrol a fingerprint to use locking."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "A security update is required before locking can be used."
            else -> "Locking isn't available on this device."
        }

    /**
     * Shows the prompt and suspends until the user resolves it.
     *
     * Note the callback does **not** itself unlock the vault. It resolves to
     * [Result.Success] and the caller runs [Vault.confirmUsable]; if the
     * Keystore disagrees, the unlock fails even though the fingerprint
     * matched. That ordering is what keeps the session flag from ever being
     * the thing that grants access.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String
    ): Result = suspendCancellableCoroutine { cont ->
        if (!canAuthenticate(activity)) {
            cont.resume(Result.Unavailable(unavailableReason(activity) ?: "Unavailable"))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Result.Success)
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (!cont.isActive) return
                    val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        code == BiometricPrompt.ERROR_CANCELED
                    cont.resume(
                        if (cancelled) Result.Cancelled else Result.Failed(message.toString())
                    )
                }

                // onAuthenticationFailed is intentionally not overridden:
                // a non-matching finger is not a terminal state, and
                // BiometricPrompt handles its own retry and lockout.
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED)
            .setConfirmationRequired(false)
            .build()

        cont.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }
}
