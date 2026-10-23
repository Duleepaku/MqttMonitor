# MQTT Monitor (Android)

Android phone ekata install karana MQTT app eka. Topics godak (15+) ekawara subscribe karala, ena hama message ekakma
aluth eka udinma pennanawa. Topic ekak haraha message publish karannath puluwan.

## Features
- **Broker tab**: broker address, port (1883 / TLS 8883), username/password, Client ID, Connect / Disconnect
- **Topics tab**: topics ona tharam add/remove (+ saha # wildcards wada karanawa). Hama topic ekatama wenama paatak.
- **Messages tab**: aluth message eka udinma, parana ewa pahalata. Topic eka, welawa, QoS pennanawa. JSON lassanata format wenawa.
  Uda dropdown eken ekama topic ekaka messages witharak filter karanna puluwan. Clear button eka.
- **Publish tab**: topic, QoS, Retain, message
- App eka close kalath / screen eka off unath background eke connection eka thiyagannawa (notification eke anthima message eka pennanawa).
- Connection eka kapunoth thaniyama reconnect wenawa. Settings saha topics save wenawa.

## APK eka hadaganna widiya 1 – GitHub (PC eke kisi deyak install karanna one na)
1. https://github.com eke free account ekak hadanna.
2. **New repository** → nama `MqttMonitor` → **Create**.
3. **uploading an existing file** link eka obala, me zip eka extract karala thiyena **hama file ekakma saha folder ekakma**
   (`.github` folder ekath ekka) drag karala danna → **Commit changes**.
   > `.github` folder eka penne nathi nam: Windows File Explorer eke View → Show → Hidden items on karanna.
4. **Actions** tab eka → "Build APK" run eka iwara wenakam (minute 3–5) inna (kola pata ✔ ekak enawa).
5. Run eka obala, pahala **Artifacts** eke **MqttMonitor-APK** download karanna → zip eka extract karama `MqttMonitor.apk` labenawa.

## APK eka hadaganna widiya 2 – Android Studio
1. Android Studio eken **Open** → me folder eka select karanna (Gradle sync wenakam inna).
2. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. APK eka: `app/build/outputs/apk/debug/app-debug.apk`

## Phone eke install karana widiya
1. APK eka phone ekata copy karanna (WhatsApp / Drive / USB).
2. File eka obanna → "Install unknown apps" allow karanna kiwwoth allow karanna → **Install**.
3. App eka open karala **Broker** tab → address eka dala **Connect**. Notification permission eka **Allow** karanna.
4. **Topics** tab eke topics add karanna.
5. Phone eka lock unath messages ganna nam: Broker tab eke **Battery optimization settings** → MQTT Monitor → *Don't optimize / Unrestricted*.

## Broker settings
| Broker | Port | TLS |
|---|---|---|
| broker.mqtt-dashboard.com (HiveMQ public) | 1883 | no |
| broker.hivemq.com | 8883 | yes |
| Own Mosquitto broker | 1883 | no |
