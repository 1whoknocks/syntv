package com.synocam.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Connection details for one Synology NAS running Surveillance Station. */
data class NasConfig(
    val host: String,
    val port: Int,
    val useHttps: Boolean,
    val account: String,
    val password: String,
    val gridColumns: Int = 2,
) {
    val baseUrl: String
        get() = (if (useHttps) "https" else "http") + "://" + host.trim() + ":" + port
}

/**
 * Stores the NAS credentials in an EncryptedSharedPreferences file so the password is
 * not kept in plaintext on the device. LAN-only app, single saved NAS.
 */
class Settings(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): NasConfig? {
        val host = prefs.getString(KEY_HOST, null)?.takeIf { it.isNotBlank() } ?: return null
        return NasConfig(
            host = host,
            port = prefs.getInt(KEY_PORT, 5000),
            useHttps = prefs.getBoolean(KEY_HTTPS, false),
            account = prefs.getString(KEY_ACCOUNT, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            gridColumns = prefs.getInt(KEY_GRID, 2),
        )
    }

    fun save(config: NasConfig) {
        prefs.edit()
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port)
            .putBoolean(KEY_HTTPS, config.useHttps)
            .putString(KEY_ACCOUNT, config.account)
            .putString(KEY_PASSWORD, config.password)
            .putInt(KEY_GRID, config.gridColumns)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val PREFS_NAME = "synocam_secure"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_HTTPS = "useHttps"
        const val KEY_ACCOUNT = "account"
        const val KEY_PASSWORD = "password"
        const val KEY_GRID = "gridColumns"
    }
}
