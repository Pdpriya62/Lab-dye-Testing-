package com.team11.labdip.data.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class BrokerSettings(
    val host: String = "broker.hivemq.com",
    val port: Int = 1883,
    val tls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val topic: String = "rvu/team11/labdip/data",
)

data class AppSettings(
    val onboarded: Boolean = false,
    val broker: BrokerSettings = BrokerSettings(),
    val passMax: Double = 1.0,
    val marginalMax: Double = 2.0,
    // Firmware BATH_TOL; only draws the band, the alert itself comes from bath_alert
    val bathTolerance: Double = 2.0,
    val luxMin: Double = 500.0,
    val luxMax: Double = 2000.0,
    val targetL: Double? = null,
    val targetA: Double? = null,
    val targetB: Double? = null,
)

private val Context.store by preferencesDataStore("settings")

/** Plain values in DataStore; the broker password is AES-GCM encrypted with an Android Keystore key. */
class SettingsRepository(private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val tls = booleanPreferencesKey("tls")
        val username = stringPreferencesKey("username")
        val passwordEnc = stringPreferencesKey("password_enc")
        val topic = stringPreferencesKey("topic")
        val passMax = doublePreferencesKey("pass_max")
        val marginalMax = doublePreferencesKey("marginal_max")
        val bathTol = doublePreferencesKey("bath_tol")
        val luxMin = doublePreferencesKey("lux_min")
        val luxMax = doublePreferencesKey("lux_max")
        val targetL = doublePreferencesKey("target_l")
        val targetA = doublePreferencesKey("target_a")
        val targetB = doublePreferencesKey("target_b")
    }

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "labdip_keyset", "labdip_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://labdip_master_key")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }
    private val associatedData = "broker_password".encodeToByteArray()

    val settings: Flow<AppSettings> = context.store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            onboarded = p[K.onboarded] ?: false,
            broker = BrokerSettings(
                host = p[K.host] ?: d.broker.host,
                port = p[K.port] ?: d.broker.port,
                tls = p[K.tls] ?: d.broker.tls,
                username = p[K.username] ?: "",
                password = decrypt(p[K.passwordEnc]),
                topic = p[K.topic] ?: d.broker.topic,
            ),
            passMax = p[K.passMax] ?: d.passMax,
            marginalMax = p[K.marginalMax] ?: d.marginalMax,
            bathTolerance = p[K.bathTol] ?: d.bathTolerance,
            luxMin = p[K.luxMin] ?: d.luxMin,
            luxMax = p[K.luxMax] ?: d.luxMax,
            targetL = p[K.targetL],
            targetA = p[K.targetA],
            targetB = p[K.targetB],
        )
    }.flowOn(Dispatchers.IO)

    suspend fun save(s: AppSettings) = withContext(Dispatchers.IO) {
        val enc = encrypt(s.broker.password)
        context.store.edit { p ->
            p[K.onboarded] = true
            p[K.host] = s.broker.host
            p[K.port] = s.broker.port
            p[K.tls] = s.broker.tls
            p[K.username] = s.broker.username
            p[K.passwordEnc] = enc
            p[K.topic] = s.broker.topic
            p[K.passMax] = s.passMax
            p[K.marginalMax] = s.marginalMax
            p[K.bathTol] = s.bathTolerance
            p[K.luxMin] = s.luxMin
            p[K.luxMax] = s.luxMax
            p.setOrRemove(K.targetL, s.targetL)
            p.setOrRemove(K.targetA, s.targetA)
            p.setOrRemove(K.targetB, s.targetB)
        }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<T>, v: T?) {
        if (v == null) remove(key) else this[key] = v
    }

    private fun encrypt(plain: String): String =
        if (plain.isEmpty()) "" else Base64.encodeToString(aead.encrypt(plain.encodeToByteArray(), associatedData), Base64.NO_WRAP)

    // A wiped Keystore key makes old ciphertext unreadable; fall back to "no password" instead of crashing
    private fun decrypt(enc: String?): String =
        if (enc.isNullOrEmpty()) "" else runCatching {
            aead.decrypt(Base64.decode(enc, Base64.NO_WRAP), associatedData).decodeToString()
        }.getOrDefault("")
}
