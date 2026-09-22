package com.dsh.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.dshStore: DataStore<Preferences> by preferencesDataStore(name = "dsh_mobile")

data class StoredSession(
    val base: String = "",
    val token: String = "",
    val device: JSONObject? = null,
    val seq: Long = 0L,
    /** The bridge counter this [seq] belongs to; see [BridgeRepository.handleHello]. */
    val epoch: String = "",
    val theme: String = "auto",
    /** 鲸鱼娘悬浮球：用户自己的开关，默认关。 */
    val overlayBall: Boolean = false,
    /** 上次读到的模型文档（原样缓存）：重开 App 不该再显示"未读取"。 */
    val modelsJson: String = "",
)

/** Everything that must survive a restart: where the desktop is, the device
 *  token, the last event seq (so a reconnect can ask for the gap), and theme. */
class SettingsStore(context: Context) {

    private val context = context.applicationContext

    private object Keys {
        val BASE = stringPreferencesKey("base")
        val TOKEN = stringPreferencesKey("token")
        val DEVICE = stringPreferencesKey("device")
        val SEQ = longPreferencesKey("seq")
        val EPOCH = stringPreferencesKey("epoch")
        val THEME = stringPreferencesKey("theme")
        val UPDATE_CHECK = longPreferencesKey("update_check")
        val UPDATE_SKIP = stringPreferencesKey("update_skip")
        val OVERLAY = booleanPreferencesKey("overlay_ball")
        val MODELS = stringPreferencesKey("models_json")
    }

    suspend fun load(): StoredSession {
        val prefs = context.dshStore.data.first()
        val device = prefs[Keys.DEVICE]?.let { raw ->
            try {
                JSONObject(raw)
            } catch (_: Exception) {
                null
            }
        }
        return StoredSession(
            base = prefs[Keys.BASE].orEmpty(),
            token = prefs[Keys.TOKEN].orEmpty(),
            device = device,
            seq = prefs[Keys.SEQ] ?: 0L,
            epoch = prefs[Keys.EPOCH].orEmpty(),
            theme = prefs[Keys.THEME] ?: "auto",
            overlayBall = prefs[Keys.OVERLAY] ?: false,
            modelsJson = prefs[Keys.MODELS].orEmpty(),
        )
    }

    suspend fun savePair(base: String, token: String, device: JSONObject) {
        context.dshStore.edit { prefs ->
            prefs[Keys.BASE] = base
            prefs[Keys.TOKEN] = token
            prefs[Keys.DEVICE] = device.toString()
        }
    }

    suspend fun saveSeq(seq: Long) {
        context.dshStore.edit { prefs -> prefs[Keys.SEQ] = seq }
    }

    suspend fun saveEpoch(epoch: String) {
        context.dshStore.edit { prefs -> prefs[Keys.EPOCH] = epoch }
    }

    /** 缓存模型文档，下次打开设置页/模型页直接就有内容。 */
    suspend fun saveModels(json: String) {
        context.dshStore.edit { prefs -> prefs[Keys.MODELS] = json }
    }

    suspend fun saveOverlay(on: Boolean) {
        context.dshStore.edit { prefs -> prefs[Keys.OVERLAY] = on }
    }

    suspend fun saveTheme(theme: String) {
        context.dshStore.edit { prefs -> prefs[Keys.THEME] = theme }
    }

    /** When the update check last ran, and which version the user waved off. */
    suspend fun loadUpdateState(): Pair<Long, String> {
        val prefs = context.dshStore.data.first()
        return (prefs[Keys.UPDATE_CHECK] ?: 0L) to prefs[Keys.UPDATE_SKIP].orEmpty()
    }

    suspend fun saveUpdateCheck(at: Long, skipVersion: String? = null) {
        context.dshStore.edit { prefs ->
            prefs[Keys.UPDATE_CHECK] = at
            if (skipVersion != null) prefs[Keys.UPDATE_SKIP] = skipVersion
        }
    }

    /** Forget the binding but keep the server address and theme. */
    suspend fun clearBinding() {
        context.dshStore.edit { prefs ->
            prefs.remove(Keys.TOKEN)
            prefs.remove(Keys.DEVICE)
            prefs.remove(Keys.SEQ)
            prefs.remove(Keys.EPOCH)
        }
    }
}
