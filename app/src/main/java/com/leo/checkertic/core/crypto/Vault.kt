package com.leo.checkertic.core.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ============================================================================
 *  THE VAULT
 * ============================================================================
 *
 * Encryption for locked categories and locked notes.
 *
 * ## Why not `androidx.security:security-crypto`
 *
 * `EncryptedSharedPreferences` / `EncryptedFile` / `MasterKey` are deprecated
 * — every API in the library was deprecated "in favour of existing platform
 * APIs and direct use of Android Keystore". So that is what this does: one
 * AES-256-GCM key, generated in and never leaving the Android Keystore,
 * hardware-backed on any device with a TEE or StrongBox.
 *
 * ## Why per-field encryption rather than SQLCipher on the whole database
 *
 * The brief asked for this to be evaluated rather than assumed, and
 * whole-database encryption is the wrong shape for this app:
 *
 *  - SQLCipher needs the passphrase at `Room.databaseBuilder` time, i.e. at
 *    process start. This app has a **home-screen widget** and a **quick-add
 *    trampoline** that both read the database from a cold process with no UI
 *    and no opportunity to prompt. Encrypting the whole DB would mean the
 *    widget shows nothing until the user unlocks the app — which breaks the
 *    single most-used surface in the product.
 *  - Locking is per-category and per-note. Whole-DB encryption protects data
 *    the user never asked to protect, at the cost of an auth prompt on every
 *    cold start, and *still* wouldn't let one category be locked while
 *    another stays open.
 *  - SQLCipher is a native dependency (~4 MB of .so across four ABIs) plus
 *    the 16 KB page-size rebuild requirement Play now enforces. That is a lot
 *    of weight for a feature that applies to a subset of rows.
 *
 * So: unlocked content stays in plain Room rows the widget can read, and
 * locked content is stored as an opaque envelope only this key can open.
 *
 * ## Key policy
 *
 * The key is created with `setUserAuthenticationRequired(true)` and a
 * **time-based** validity window rather than auth-per-use. Auth-per-use keys
 * must be unlocked through a `BiometricPrompt.CryptoObject`, and the platform
 * forbids combining a CryptoObject with `DEVICE_CREDENTIAL` fallback. The
 * brief asked for "biometric/device-credential auth", and a user with no
 * enrolled fingerprint still needs a way in, so the time-based form is the
 * only one that satisfies both. The trade-off is that the key stays usable
 * for [AUTH_VALIDITY_SECONDS] after any successful device authentication;
 * that window is deliberately short.
 *
 * `setInvalidatedByBiometricEnrollment(true)` means enrolling a new
 * fingerprint destroys the key. That is the correct security posture — it
 * stops someone who has your unlocked phone from adding their own finger and
 * reading your vault — but it does mean locked content becomes unreadable.
 * [isKeyInvalidated] detects this so the UI can say so plainly instead of
 * showing a decrypt error.
 */
object Vault {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "flip_vault_key_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    /** Envelope prefix, so a plaintext row can never be mistaken for ciphertext. */
    private const val ENVELOPE_PREFIX = "flipv1:"

    /**
     * How long a successful unlock keeps the key usable.
     *
     * Five minutes: long enough to read a locked note, add a few tasks and
     * come back from a notification without re-prompting; short enough that
     * a phone left on a desk re-locks before anyone wanders past.
     */
    const val AUTH_VALIDITY_SECONDS = 300

    private val _unlocked = MutableStateFlow(false)

    /**
     * Session unlock state.
     *
     * This mirrors the Keystore's own auth window rather than replacing it.
     * Flipping this flag alone can't decrypt anything — if it were somehow
     * forced true without a real authentication, the Keystore still throws
     * `UserNotAuthenticatedException`. It exists so the UI knows whether to
     * render content or a lock placeholder.
     */
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val secureRandom = SecureRandom()

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? =
        runCatching { keyStore().getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()

    /** True once a vault key has ever been created on this device. */
    fun hasKey(): Boolean = runCatching { keyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false)

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setInvalidatedByBiometricEnrollment(true)
                }
                // StrongBox where the hardware has it; fall back silently
                // where it doesn't, which is handled in [ensureKey].
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun createKeyWithoutStrongBox(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setInvalidatedByBiometricEnrollment(true)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * Returns the vault key, creating it on first use.
     *
     * StrongBox is requested first and retried without it on failure: several
     * shipping devices advertise StrongBox but reject 256-bit AES in it, and
     * the failure surfaces as a `StrongBoxUnavailableException` only at
     * generation time.
     */
    @Synchronized
    fun ensureKey(): SecretKey {
        existingKey()?.let { return it }
        return runCatching { createKey() }.getOrElse { createKeyWithoutStrongBox() }
    }

    /**
     * True when the key exists but has been invalidated — almost always
     * because a new biometric was enrolled.
     */
    fun isKeyInvalidated(): Boolean {
        if (!hasKey()) return false
        return try {
            Cipher.getInstance(TRANSFORM).init(Cipher.ENCRYPT_MODE, ensureKey())
            false
        } catch (_: UserNotAuthenticatedException) {
            false // just needs auth, key is fine
        } catch (_: Exception) {
            true
        }
    }

    internal fun markUnlocked() {
        _unlocked.value = true
    }

    /** Drops the session flag. Called on lock-now and when the app backgrounds. */
    fun lock() {
        _unlocked.value = false
    }

    /**
     * Verifies the key is genuinely usable right now.
     *
     * Called immediately after a successful `BiometricPrompt` so a stale
     * session flag can never be mistaken for a live one: this performs a real
     * (throwaway) cipher init, which is the only thing that actually proves
     * the Keystore auth window is open.
     */
    fun confirmUsable(): Boolean = try {
        Cipher.getInstance(TRANSFORM).init(Cipher.ENCRYPT_MODE, ensureKey())
        markUnlocked()
        true
    } catch (_: Exception) {
        _unlocked.value = false
        false
    }

    // -- String envelopes ---------------------------------------------------

    fun isEnvelope(value: String): Boolean = value.startsWith(ENVELOPE_PREFIX)

    /**
     * Encrypts [plain] into `flipv1:<base64(iv || ciphertext||tag)>`.
     *
     * A fresh random IV per call is non-negotiable with GCM — reusing one
     * across two messages under the same key leaks both.
     */
    fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, ensureKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + body.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(body, 0, packed, iv.size, body.size)
        return ENVELOPE_PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /**
     * Opens an envelope, or returns null if the vault is locked, the key was
     * invalidated, or the data was tampered with (GCM authenticates, so a
     * modified row fails here rather than returning garbage).
     */
    fun open(envelope: String): String? {
        if (!isEnvelope(envelope)) return envelope
        return try {
            val packed = Base64.decode(envelope.removePrefix(ENVELOPE_PREFIX), Base64.NO_WRAP)
            if (packed.size <= IV_BYTES) return null
            val iv = packed.copyOfRange(0, IV_BYTES)
            val body = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, ensureKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (_: UserNotAuthenticatedException) {
            _unlocked.value = false
            null
        } catch (_: Exception) {
            null
        }
    }

    // -- Byte envelopes, for voice recordings -------------------------------

    fun sealBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, ensureKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun openBytes(packed: ByteArray): ByteArray? = try {
        if (packed.size <= IV_BYTES) null else {
            val iv = packed.copyOfRange(0, IV_BYTES)
            val body = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, ensureKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(body)
        }
    } catch (_: Exception) {
        null
    }

    /** Placeholder shown in place of content the vault can't currently open. */
    const val LOCKED_PLACEHOLDER = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022"
}
