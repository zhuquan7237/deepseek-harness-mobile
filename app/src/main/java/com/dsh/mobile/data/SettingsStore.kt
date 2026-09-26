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
    /** 新对话默认模型（用户设置的；空 = 跟随电脑端当前模型）。 */
    val defaultProvider: String = "",
    val defaultModel: String = "",
    val defaultLabel: String = "",
)

/** 「从上游同步」最近一次的结果：成功给时间，失败给原因（模型页展示用）。 */
data class ProviderSync(val at: Long = 0L, val ok: Boolean = true, val msg: String = "")

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
        val PROVIDER_SYNC = stringPreferencesKey("provider_sync_json")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val DEFAULT_LABEL = stringPreferencesKey("default_label")
        val HOST_ALIAS = stringPreferencesKey("host_alias")
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
            defaultProvider = prefs[Keys.DEFAULT_PROVIDER].orEmpty(),
            defaultModel = prefs[Keys.DEFAULT_MODEL].orEmpty(),
            defaultLabel = prefs[Keys.DEFAULT_LABEL].orEmpty(),
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

    /**
     * 「从上游同步」最近一次的结果（成功给时间、失败给原因），按提供商记。
     * 存成一条 JSON 字符串，模型页一次读全，避免逐个 suspend 读 DataStore。
     */
    suspend fun saveProviderSync(providerId: String, sync: ProviderSync) {
        context.dshStore.edit { prefs ->
            val current = prefs[Keys.PROVIDER_SYNC]?.let { raw ->
                runCatching { JSONObject(raw) }.getOrNull()
            } ?: JSONObject()
            current.put(
                providerId,
                JSONObject().put("at", sync.at).put("ok", sync.ok).put("msg", sync.msg),
            )
            prefs[Keys.PROVIDER_SYNC] = current.toString()
        }
    }

    suspend fun loadProviderSyncMap(): Map<String, ProviderSync> {
        val raw = context.dshStore.data.first()[Keys.PROVIDER_SYNC].orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<String, ProviderSync>()
        for (key in obj.keys()) {
            when (val value = obj.get(key)) {
                // 旧格式（一个时间戳）当作成功读，兼容老版本写下的数据
                is Long -> result[key] = ProviderSync(at = value, ok = true)
                is JSONObject -> result[key] = ProviderSync(
                    at = value.optLong("at"),
                    ok = value.optBoolean("ok", true),
                    msg = value.optString("msg"),
                )
            }
        }
        return result
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

    /** 新对话默认模型：provider/model 空字符串表示"跟随电脑端"。 */
    suspend fun saveDefaultModel(provider: String, model: String, label: String) {
        context.dshStore.edit { prefs ->
            prefs[Keys.DEFAULT_PROVIDER] = provider
            prefs[Keys.DEFAULT_MODEL] = model
            prefs[Keys.DEFAULT_LABEL] = label
        }
    }

    /** 电脑昵称（只在这台手机显示）：空 = 回到电脑自己的名字。 */
    suspend fun saveHostAlias(alias: String) {
        context.dshStore.edit { prefs ->
            prefs[Keys.HOST_ALIAS] = alias.trim()
        }
    }

    suspend fun loadHostAlias(): String =
        context.dshStore.data.first()[Keys.HOST_ALIAS].orEmpty()
}
