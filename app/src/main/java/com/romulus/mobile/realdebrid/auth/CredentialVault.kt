package com.romulus.mobile.realdebrid.auth

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.time.Instant

data class StoredTokenRecord(val encryptedValue: ByteArray, val savedAt: Instant)

interface CredentialVault {
    suspend fun readToken(): StoredTokenRecord?

    suspend fun readPlainToken(): Result<String?>

    suspend fun writeToken(token: String, savedAt: Instant): Result<Unit>

    suspend fun clearToken(): Result<Unit>
}

internal class SharedPreferencesCredentialVault(
    context: Context,
    private val tokenCipher: TokenCipher
) : CredentialVault {
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun readToken(): StoredTokenRecord? {
        val encrypted = sharedPreferences.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        val savedAtEpochMillis = sharedPreferences.getLong(KEY_SAVED_AT_EPOCH_MILLIS, 0L)
        return StoredTokenRecord(
            encryptedValue = encrypted.toByteArray(StandardCharsets.UTF_8),
            savedAt = Instant.ofEpochMilli(savedAtEpochMillis)
        )
    }

    override suspend fun readPlainToken(): Result<String?> {
        val record = readToken() ?: return Result.success(null)
        val encrypted = String(record.encryptedValue, StandardCharsets.UTF_8)
        return runCatching { tokenCipher.decrypt(encrypted) }
    }

    override suspend fun writeToken(token: String, savedAt: Instant): Result<Unit> = runCatching {
        val encrypted = tokenCipher.encrypt(token)
        val editor = sharedPreferences.edit()
        editor.putString(KEY_ENCRYPTED_TOKEN, encrypted)
        editor.putLong(KEY_SAVED_AT_EPOCH_MILLIS, savedAt.toEpochMilli())
        check(editor.commit()) { "Encrypted token could not be persisted" }
        Unit
    }

    override suspend fun clearToken(): Result<Unit> = runCatching {
        val editor = sharedPreferences.edit()
        editor.remove(KEY_ENCRYPTED_TOKEN)
        editor.remove(KEY_SAVED_AT_EPOCH_MILLIS)
        check(editor.commit()) { "Encrypted token could not be cleared" }
        Unit
    }

    private companion object {
        const val PREFERENCES_NAME = "realdebrid_credentials"
        const val KEY_ENCRYPTED_TOKEN = "encrypted_token"
        const val KEY_SAVED_AT_EPOCH_MILLIS = "saved_at_epoch_millis"
    }
}

internal class InMemoryCredentialVault(
    initialToken: String? = null,
    private val tokenCipher: TokenCipher = PassthroughTokenCipher()
) : CredentialVault {
    private var encryptedValue: String? = initialToken?.let(tokenCipher::encrypt)
    private var savedAt: Instant? = initialToken?.let { Instant.EPOCH }

    override suspend fun readToken(): StoredTokenRecord? {
        val value = encryptedValue ?: return null
        val persistedAt = savedAt ?: Instant.EPOCH
        return StoredTokenRecord(
            encryptedValue = value.toByteArray(StandardCharsets.UTF_8),
            savedAt = persistedAt
        )
    }

    override suspend fun readPlainToken(): Result<String?> {
        val value = encryptedValue ?: return Result.success(null)
        return runCatching { tokenCipher.decrypt(value) }
    }

    override suspend fun writeToken(token: String, savedAt: Instant): Result<Unit> = runCatching {
        encryptedValue = tokenCipher.encrypt(token)
        this.savedAt = savedAt
        Unit
    }

    override suspend fun clearToken(): Result<Unit> = runCatching {
        encryptedValue = null
        savedAt = null
        Unit
    }
}

internal interface TokenCipher {
    fun encrypt(plainText: String): String

    fun decrypt(cipherText: String): String
}

internal class PassthroughTokenCipher : TokenCipher {
    override fun encrypt(plainText: String): String = Base64.encodeToString(
        plainText.toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP
    )

    override fun decrypt(cipherText: String): String = String(
        Base64.decode(cipherText, Base64.NO_WRAP),
        StandardCharsets.UTF_8
    )
}
