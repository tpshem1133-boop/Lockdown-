package com.lockdown.strictblocker.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 4-digit vault PIN, stored as a salted SHA-256 hash inside
 * EncryptedSharedPreferences (AES-256). The PIN itself is never persisted.
 *
 * Brute-force guard: 5 wrong attempts lock verification for 1 minute.
 * Recovery ("Forgot PIN") is only possible when Strict Mode is NOT active:
 * the user answers the security question chosen at setup, or waits 24 hours.
 */
class PinManager private constructor(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(): Boolean = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, hash(salt, pin))
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
    }

    fun setSecurityQuestion(question: String, answer: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SEC_Q, question)
            .putString(KEY_SEC_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_SEC_HASH, hash(salt, answer.trim().lowercase()))
            .apply()
    }

    fun securityQuestion(): String? = prefs.getString(KEY_SEC_Q, null)

    fun verifySecurityAnswer(answer: String): Boolean {
        val salt = prefs.getString(KEY_SEC_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return false
        val expected = prefs.getString(KEY_SEC_HASH, null) ?: return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            hash(salt, answer.trim().lowercase()).toByteArray(Charsets.UTF_8)
        )
    }

    sealed interface VerifyResult {
        data object Success : VerifyResult
        data class Wrong(val attemptsLeft: Int) : VerifyResult
        data class Locked(val remainingMillis: Long) : VerifyResult
    }

    fun verify(pin: String): VerifyResult {
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (lockoutUntil > now) return VerifyResult.Locked(lockoutUntil - now)

        val salt = prefs.getString(KEY_PIN_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return VerifyResult.Wrong(MAX_ATTEMPTS)
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return VerifyResult.Wrong(MAX_ATTEMPTS)

        val ok = MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            hash(salt, pin).toByteArray(Charsets.UTF_8)
        )
        if (ok) {
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).apply()
            return VerifyResult.Success
        }
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        if (failed >= MAX_ATTEMPTS) {
            prefs.edit()
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCKOUT_UNTIL, now + LOCKOUT_MILLIS)
                .apply()
            return VerifyResult.Locked(LOCKOUT_MILLIS)
        }
        prefs.edit().putInt(KEY_FAILED, failed).apply()
        return VerifyResult.Wrong(MAX_ATTEMPTS - failed)
    }

    /** Records when a "wait 24 hours" PIN recovery was requested. */
    var recoveryRequestedAt: Long
        get() = prefs.getLong(KEY_RECOVERY_AT, 0L)
        set(v) = prefs.edit().putLong(KEY_RECOVERY_AT, v).apply()

    fun clearPin() {
        prefs.edit().clear().apply()
    }

    private fun hash(salt: ByteArray, value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    companion object {
        private const val FILE = "vault_secure"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SEC_Q = "sec_q"
        private const val KEY_SEC_SALT = "sec_salt"
        private const val KEY_SEC_HASH = "sec_hash"
        private const val KEY_FAILED = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_RECOVERY_AT = "recovery_requested_at"
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 60_000L
        const val RECOVERY_WAIT_MILLIS = 24L * 60 * 60 * 1000

        @Volatile
        private var instance: PinManager? = null

        fun get(context: Context): PinManager =
            instance ?: synchronized(this) {
                instance ?: PinManager(context).also { instance = it }
            }
    }
}
