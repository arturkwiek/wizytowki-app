package com.wizytowki.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides the SQLCipher passphrase.
 *
 * On first use a random 32-byte passphrase is generated, encrypted with an AES-GCM key that
 * never leaves the Android Keystore, and stored (as IV + ciphertext) in private SharedPreferences.
 * Only the wrapped form ever touches disk.
 */
class DatabaseKeyProvider(private val context: Context) {

    @Synchronized
    fun getPassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(PREF_WRAPPED_KEY, null)
        if (stored != null) {
            return unwrap(Base64.decode(stored, Base64.NO_WRAP))
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = wrap(passphrase)
        check(prefs.edit().putString(PREF_WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()) {
            "Nie udało się zapisać klucza bazy"
        }
        return passphrase
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(data: ByteArray): ByteArray {
        val iv = data.copyOfRange(0, GCM_IV_BYTES)
        val cipherText = data.copyOfRange(GCM_IV_BYTES, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "wizytowki_db_master_key"
        const val PREFS_NAME = "wizytowki_db_key"
        const val PREF_WRAPPED_KEY = "wrapped_passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
