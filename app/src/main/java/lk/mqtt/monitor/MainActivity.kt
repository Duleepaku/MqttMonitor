package lk.mqtt.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 15 clearly different colours, one per subscribed topic. */
private val PALETTE: IntArray = listOf(
    "#1E88E5", "#E53935", "#43A047", "#FB8C00", "#8E24AA",
    "#00ACC1", "#D81B60", "#7CB342", "#5E35B1", "#F4511E",
    "#00897B", "#C0CA33", "#3949AB", "#8D6E63", "#78909C"
).map { Color.parseColor(it) }.toIntArray()

/** MQTT wildcard match: + is one level, # is everything below. */
fun topicMatches(filter: String, topic: String): Boolean {
    val f = filter.split("/")
    val t = topic.split("/")
    for (i in f.indices) {
        val p = f[i]
        if (p == "#") return true
        if (i >= t.size) return false
        if (p != "+" && p != t[i]) return false
    }
    return f.size == t.size
}

fun prettyPayload(payload: String): String {
    val s = payload.trim()
    return try {
        when {
            s.startsWith("{") -> JSONObject(s).toString(2)
            s.startsWith("[") -> JSONArray(s).toString(2)
            else -> payload
        }
    } catch (e: Exception) {
        payload
    }
}

class MsgVH(v: View) : RecyclerView.ViewHolder(v) {
    val bar: View = v.findViewById(R.id.bar)
    val topic: TextView = v.findViewById(R.id.tvMsgTopic)
    val meta: TextView = v.findViewById(R.id.tvMeta)
    val body: TextView = v.findViewById(R.id.tvBody)
}

class MsgAdapter(private val colorFor: (String) -> Int) : RecyclerView.Adapter<MsgVH>() {
    private val items = ArrayList<Msg>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgVH =
        MsgVH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: MsgVH, position: Int) {
        val m = items[position]
        val c = colorFor(m.topic)
        h.bar.setBackgroundColor(c)
        h.topic.setTextColor(c)
        h.topic.text = m.topic
        val retained = if (m.retained) " · retained" else ""
        h.meta.text = "${fmt.format(Date(m.time))} · Q${m.qos}$retained"
        h.body.text = prettyPayload(m.payload)
    }

    fun setAll(list: List<Msg>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** New message goes to the top; older ones move down. */
    fun addTop(m: Msg) {
        items.add(0, m)
        notifyItemInserted(0)
        if (items.size > MqttManager.MAX_MESSAGES) {
            items.removeAt(items.size - 1)
            notifyItemRemoved(items.size)
        }
    }
}

class MainActivity : AppCompatActivity(), MqttManager.Listener {

    private lateinit var prefs: Prefs
    private lateinit var adapter: MsgAdapter
    private lateinit var rv: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var spFilter: Spinner
    private lateinit var llTopics: LinearLayout
    private lateinit var tvTopicCount: TextView
    private lateinit var pages: List<View>

    private var subs: List<TopicSub> = emptyList()
    private var filter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvEmpty = findViewById(R.id.tvEmpty)
        spFilter = findViewById(R.id.spFilter)
        llTopics = findViewById(R.id.llTopics)
        tvTopicCount = findViewById(R.id.tvTopicCount)
        pages = listOf(
            findViewById(R.id.pageMessages),
            findViewById(R.id.pageTopics),
            findViewById(R.id.pagePublish),
            findViewById(R.id.pageBroker)
        )

        // ---- tabs ----
        val tabs = findViewById<TabLayout>(R.id.tabs)
        listOf("Messages", "Topics", "Publish", "Broker").forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { showPage(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // ---- messages ----
        rv = findViewById(R.id.rvMessages)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MsgAdapter { colorFor(it) }
        rv.adapter = adapter
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            MqttManager.clearMessages()
            refreshMessages()
        }
        spFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newFilter = if (position == 0) null else subs.getOrNull(position - 1)?.topic
                if (newFilter != filter) {
                    filter = newFilter
                    refreshMessages()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ---- topics ----
        val etTopic = findViewById<EditText>(R.id.etTopic)
        val spSubQos = qosSpinner(R.id.spSubQos)
        findViewById<Button>(R.id.btnAddTopic).setOnClickListener {
            val t = etTopic.text.toString().trim()
            when {
                t.isEmpty() -> toast("Topic eka type karanna")
                subs.any { it.topic == t } -> toast("Me topic eka already subscribe karala")
                else -> {
                    val q = spSubQos.selectedItemPosition
                    prefs.topics = subs + TopicSub(t, q)
                    if (MqttManager.isConnected) MqttManager.subscribe(t, q)
                    etTopic.setText("")
                    renderTopics()
                    toast("Subscribed: $t")
                }
            }
        }

        // ---- publish ----
        val etPubTopic = findViewById<EditText>(R.id.etPubTopic)
        val etPubMsg = findViewById<EditText>(R.id.etPubMsg)
        val spPubQos = qosSpinner(R.id.spPubQos)
        val cbRetain = findViewById<CheckBox>(R.id.cbRetain)
        etPubTopic.setText(prefs.pubTopic)
        findViewById<Button>(R.id.btnPublish).setOnClickListener {
            val t = etPubTopic.text.toString().trim()
            when {
                t.isEmpty() -> toast("Publish topic eka type karanna")
                !MqttManager.isConnected -> toast("Broker ekata connect wela na")
                else -> {
                    prefs.pubTopic = t
                    val ok = MqttManager.publish(t, etPubMsg.text.toString(), spPubQos.selectedItemPosition, cbRetain.isChecked)
                    toast(if (ok) "Published" else "Publish failed")
                }
            }
        }

        // ---- broker ----
        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val cbTls = findViewById<CheckBox>(R.id.cbTls)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val etCid = findViewById<EditText>(R.id.etClientId)
        etHost.setText(prefs.host)
        etPort.setText(prefs.port.toString())
        cbTls.isChecked = prefs.tls
        etUser.setText(prefs.user)
        etPass.setText(prefs.pass)
        etCid.setText(prefs.clientId)
        cbTls.setOnCheckedChangeListener { _, checked ->
            val p = etPort.text.toString()
            if (checked && p == "1883") etPort.setText("8883")
            if (!checked && p == "8883") etPort.setText("1883")
        }
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val host = etHost.text.toString().trim()
            if (host.isEmpty()) {
                toast("Broker address eka type karanna")
                return@setOnClickListener
            }
            prefs.host = host
            prefs.port = etPort.text.toString().toIntOrNull() ?: 1883
            prefs.tls = cbTls.isChecked
            prefs.user = etUser.text.toString().trim()
            prefs.pass = etPass.text.toString()
            prefs.clientId = etCid.text.toString().trim()
            prefs.autoConnect = true
            askNotificationPermission()
            startMqttService()
            tabs.getTabAt(0)?.select()
        }
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            prefs.autoConnect = false
            stopService(Intent(this, MqttService::class.java))
            MqttManager.disconnect()
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                toast("Settings > Apps > MQTT Monitor > Battery eke 'Unrestricted' danna")
            }
        }

        renderTopics()
        showPage(0)

        if (prefs.autoConnect && !MqttService.running) startMqttService()
    }

    override fun onResume() {
        super.onResume()
        MqttManager.addListener(this)
        onStatus(MqttManager.statusText, MqttManager.isConnected)
        refreshMessages()
    }

    override fun onPause() {
        MqttManager.removeListener(this)
        super.onPause()
    }

    // ---- MqttManager.Listener ----

    override fun onStatus(text: String, connected: Boolean) {
        tvStatus.text = text
        val color = when {
            connected -> R.color.ok
            text == "Disconnected" -> R.color.idle
            text.startsWith("Connecting") || text.contains("reconnecting", true) || text.contains("retrying", true) -> R.color.warn
            else -> R.color.bad
        }
        tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, color))
        updateEmpty()
    }

    override fun onMessage(m: Msg) {
        if (!passesFilter(m.topic)) return
        val lm = rv.layoutManager as LinearLayoutManager
        val atTop = lm.findFirstVisibleItemPosition() <= 0
        adapter.addTop(m)
        if (atTop) rv.scrollToPosition(0)
        updateEmpty()
    }

    // ---- helpers ----

    private fun startMqttService() {
        ContextCompat.startForegroundService(this, Intent(this, MqttService::class.java))
    }

    private fun showPage(i: Int) {
        pages.forEachIndexed { idx, v -> v.visibility = if (idx == i) View.VISIBLE else View.GONE }
    }

    private fun qosSpinner(id: Int): Spinner {
        val s = findViewById<Spinner>(id)
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("QoS 0", "QoS 1", "QoS 2"))
        return s
    }

    private fun colorFor(topic: String): Int {
        val idx = subs.indexOfFirst { topicMatches(it.topic, topic) }
        val i = if (idx >= 0) idx else Math.floorMod(topic.hashCode(), PALETTE.size)
        return PALETTE[i % PALETTE.size]
    }

    private fun passesFilter(topic: String): Boolean {
        val f = filter ?: return true
        return topicMatches(f, topic)
    }

    private fun refreshMessages() {
        adapter.setAll(MqttManager.messages.filter { passesFilter(it.topic) })
        rv.scrollToPosition(0)
        updateEmpty()
    }

    private fun updateEmpty() {
        tvEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        tvEmpty.text = when {
            subs.isEmpty() -> "Topics tab eke topic ekak subscribe karanna. Ethakota messages methana pennanawa."
            !MqttManager.isConnected -> "Broker tab eken Connect karanna."
            else -> "Messages enakam balan inna…"
        }
    }

    private fun renderTopics() {
        subs = prefs.topics
        llTopics.removeAllViews()
        subs.forEachIndexed { i, s ->
            val row = layoutInflater.inflate(R.layout.item_topic, llTopics, false)
            row.findViewById<View>(R.id.dot).backgroundTintList = ColorStateList.valueOf(PALETTE[i % PALETTE.size])
            row.findViewById<TextView>(R.id.tvTopic).text = s.topic
            row.findViewById<TextView>(R.id.tvQos).text = "QoS ${s.qos}"
            row.findViewById<Button>(R.id.btnRemove).setOnClickListener {
                prefs.topics = prefs.topics.filter { it.topic != s.topic }
                MqttManager.unsubscribe(s.topic)
                if (filter == s.topic) filter = null
                renderTopics()
                refreshMessages()
            }
            llTopics.addView(row)
        }
        tvTopicCount.text = when (subs.size) {
            0 -> "No topics yet"
            1 -> "1 subscribed topic"
            else -> "${subs.size} subscribed topics"
        }

        // filter dropdown: All + each subscribed topic
        val names = listOf("All topics (${subs.size})") + subs.map { it.topic }
        spFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val sel = filter?.let { f -> subs.indexOfFirst { it.topic == f } + 1 } ?: 0
        spFilter.setSelection(if (sel > 0) sel else 0)
        updateEmpty()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
