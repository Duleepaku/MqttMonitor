package lk.mqtt.monitor

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

data class Msg(
    val topic: String,
    val payload: String,
    val qos: Int,
    val retained: Boolean,
    val time: Long
)

/** Holds the single MQTT connection and the received message list for the whole app. */
object MqttManager {
    const val MAX_MESSAGES = 1000

    interface Listener {
        fun onMessage(m: Msg) {}
        fun onStatus(text: String, connected: Boolean) {}
    }

    /** Newest message is at index 0. */
    val messages = ArrayList<Msg>()

    var statusText = "Disconnected"
        private set
    var isConnected = false
        private set

    private val listeners = LinkedHashSet<Listener>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var client: MqttAsyncClient? = null
    private var options: MqttConnectOptions? = null
    private var appCtx: Context? = null
    @Volatile private var wanted = false
    private val retry = Runnable { tryConnect() }

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun setStatus(text: String, connected: Boolean) {
        main.post {
            statusText = text
            isConnected = connected
            listeners.toList().forEach { it.onStatus(text, connected) }
        }
    }

    private fun pushMessage(m: Msg) {
        main.post {
            messages.add(0, m)
            while (messages.size > MAX_MESSAGES) messages.removeAt(messages.size - 1)
            listeners.toList().forEach { it.onMessage(m) }
        }
    }

    fun clearMessages() { messages.clear() }

    fun connect(ctx: Context) {
        appCtx = ctx.applicationContext
        val p = Prefs(ctx)
        closeClient()
        wanted = true
        val scheme = if (p.tls) "ssl" else "tcp"
        val uri = "$scheme://${p.host.trim()}:${p.port}"
        try {
            val c = MqttAsyncClient(uri, p.clientId, MemoryPersistence())
            c.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    if (client !== c) {
                        // An old connection came back after the user switched brokers: drop it.
                        Thread { try { c.disconnectForcibly(500, 1000) } catch (e: Exception) { } }.start()
                        return
                    }
                    setStatus("Connected", true)
                    subscribeAll()
                }

                override fun connectionLost(cause: Throwable?) {
                    if (client === c) setStatus("Connection lost, reconnecting…", false)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (client !== c || topic == null || message == null) return
                    pushMessage(
                        Msg(
                            topic,
                            String(message.payload, Charsets.UTF_8),
                            message.qos,
                            message.isRetained,
                            System.currentTimeMillis()
                        )
                    )
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                maxInflight = 100
                if (p.user.isNotBlank()) {
                    userName = p.user
                    password = p.pass.toCharArray()
                }
            }
            client = c
            tryConnect()
        } catch (e: Exception) {
            setStatus("Error: ${e.message}", false)
        }
    }

    private fun tryConnect() {
        val c = client ?: return
        val o = options ?: return
        if (!wanted) return
        setStatus("Connecting…", false)
        try {
            c.connect(o, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {}
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    if (client !== c || !wanted) return
                    setStatus("Connect failed: ${exception?.message ?: "unknown"}, retrying", false)
                    main.postDelayed(retry, 5000)
                }
            })
        } catch (e: Exception) {
            setStatus("Connect failed: ${e.message}, retrying", false)
            main.postDelayed(retry, 5000)
        }
    }

    private fun subscribeAll() {
        val ctx = appCtx ?: return
        val c = client ?: return
        val subs = Prefs(ctx).topics
        if (subs.isEmpty()) return
        try {
            c.subscribe(subs.map { it.topic }.toTypedArray(), subs.map { it.qos }.toIntArray())
        } catch (e: Exception) {
            setStatus("Subscribe failed: ${e.message}", true)
        }
    }

    fun subscribe(topic: String, qos: Int): Boolean {
        val c = client ?: return false
        if (!c.isConnected) return false
        return try {
            c.subscribe(topic, qos)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun unsubscribe(topic: String) {
        val c = client ?: return
        if (!c.isConnected) return
        try { c.unsubscribe(topic) } catch (e: Exception) { }
    }

    fun publish(topic: String, payload: String, qos: Int, retain: Boolean): Boolean {
        val c = client ?: return false
        if (!c.isConnected) return false
        return try {
            c.publish(topic, payload.toByteArray(Charsets.UTF_8), qos, retain)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disconnect() {
        wanted = false
        closeClient()
        setStatus("Disconnected", false)
    }

    private fun closeClient() {
        main.removeCallbacks(retry)
        val c = client ?: return
        client = null
        Thread {
            try { c.disconnect(0).waitForCompletion(1500) } catch (e: Exception) { }
            try { c.disconnectForcibly(500, 1000) } catch (e: Exception) { }
            try { c.close() } catch (e: Exception) { }
        }.start()
    }
}
