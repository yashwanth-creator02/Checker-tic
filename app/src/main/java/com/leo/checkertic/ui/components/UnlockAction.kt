package com.leo.checkertic.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.leo.checkertic.core.crypto.BiometricGate
import com.leo.checkertic.core.crypto.Vault
import kotlinx.coroutines.launch

/**
 * Finds the hosting [FragmentActivity].
 *
 * `BiometricPrompt` needs one because it hosts its own fragment for lifecycle
 * safety. `LocalContext` inside Compose is usually a `ContextWrapper` chain
 * rather than the Activity itself, so it has to be unwrapped.
 */
internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Returns a callback that prompts for biometric / device-credential auth and
 * reports whether the vault genuinely opened.
 *
 * Note the two-step confirmation: a successful prompt is not treated as an
 * unlock on its own. [Vault.confirmUsable] performs a real cipher init
 * afterwards, so the session flag can only ever be set by the Keystore
 * actually agreeing — not by the callback claiming it should be.
 */
@Composable
fun rememberVaultUnlock(): (String, (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return { reason: String, onResult: (Boolean) -> Unit ->
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onResult(false)
        } else {
            scope.launch {
                when (val result = BiometricGate.authenticate(activity, "Unlock Flip", reason)) {
                    is BiometricGate.Result.Success -> onResult(Vault.confirmUsable())
                    is BiometricGate.Result.Cancelled -> onResult(false)
                    is BiometricGate.Result.Unavailable -> {
                        Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                        onResult(false)
                    }
                    is BiometricGate.Result.Failed -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        onResult(false)
                    }
                }
            }
        }
    }
}
