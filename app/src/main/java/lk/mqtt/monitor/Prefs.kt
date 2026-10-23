package lk.mqtt.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TopicSub(val topic: String, val qos: Int)

/** Saved settings: broker details and the list of subscribed topics. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("mqtt", Context.MODE_PRIVATE)

    var host: String
        get() = sp.getString("host", "broker.mqtt-dashboard.com") ?: ""
        set(v) = sp.edit().putString("host", v).apply()

    var port: Int
        get() = sp.getInt("port", 1883)
        set(v) = sp.edit().putInt("port", v).apply()

    var tls: Boolean
        get() = sp.getBoolean("tls", false)
        set(v) = sp.edit().putBoolean("tls", v).apply()

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) = sp.edit().putString("user", v).apply()

    var pass: String
        get() = sp.getString("pass", "") ?: ""
        set(v) = sp.edit().putString("pass", v).apply()

    var clientId: String
        get() {
            var id = sp.getString("cid", "") ?: ""
            if (id.isBlank()) {
                id = "android_" + UUID.randomUUID().toString().take(8)
                sp.edit().putString("cid", id).apply()
            }
            return id
        }
        set(v) = sp.edit().putString("cid", v).apply()

    /** True after the user taps Connect; the app reconnects by itself when reopened. */
    var autoConnect: Boolean
        get() = sp.getBoolean("auto", false)
        set(v) = sp.edit().putBoolean("auto", v).apply()

    var pubTopic: String
        get() = sp.getString("pubTopic", "") ?: ""
        set(v) = sp.edit().putString("pubTopic", v).apply()

    var topics: List<TopicSub>
        get() {
            val arr = try {
                JSONArray(sp.getString("topics", "[]"))
            } catch (e: Exception) {
                JSONArray()
            }
            val out = ArrayList<TopicSub>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(TopicSub(o.optString("t"), o.optInt("q", 0)))
            }
            return out
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("t", it.topic).put("q", it.qos)) }
            sp.edit().putString("topics", arr.toString()).apply()
        }
}
