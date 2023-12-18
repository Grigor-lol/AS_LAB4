package com.example.inventory.data


import android.app.Application
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.inventory.MASTER_KEY

object Settings {
    private lateinit var app: Application
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var masterKey: MasterKey

    fun init(context: Application) {
        app = context
        masterKey = MasterKey.Builder(app, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        MASTER_KEY = masterKey
        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            SETTINGS_PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var defaultProviderName: String
        get() = sharedPreferences.getString(DEF_NAME_KEY, "")!!
        set(value) {
            sharedPreferences.edit().putString(DEF_NAME_KEY, value).apply()
        }

    var defaultProviderPhoneNumber: String
        get() = sharedPreferences.getString(DEF_PHONE_KEY, "")!!
        set(value) {
            sharedPreferences.edit().putString(DEF_PHONE_KEY, value).apply()
        }

    var defaultProviderEmail: String
        get() = sharedPreferences.getString(DEF_EMAIL_KEY, "")!!
        set(value) {
            sharedPreferences.edit().putString(DEF_EMAIL_KEY, value).apply()
        }

    var enableDefaultFields: Boolean
        get() = sharedPreferences.getBoolean(ENABLE_DEF_FIELDS_KEY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(ENABLE_DEF_FIELDS_KEY, value).apply()
        }

    var hideSensitiveData: Boolean
        get() = sharedPreferences.getBoolean(HIDE_SENSITIVE_DATA_KEY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(HIDE_SENSITIVE_DATA_KEY, value).apply()
        }

    var disableSharing: Boolean
        get() = sharedPreferences.getBoolean(DISABLE_SHARING_KEY, false)
        set(value) {
            sharedPreferences.edit().putBoolean(DISABLE_SHARING_KEY, value).apply()
        }

    private const val SETTINGS_PREFERENCES_NAME = "app_settings"
    private const val DEF_NAME_KEY = "default_shipper_name"
    private const val DEF_PHONE_KEY = "default_shipper_phone"
    private const val DEF_EMAIL_KEY = "default_shipper_email"
    private const val ENABLE_DEF_FIELDS_KEY = "enable_def_fields"
    private const val HIDE_SENSITIVE_DATA_KEY = "hide_sensitive_data"
    private const val DISABLE_SHARING_KEY = "disable_sharing"
}