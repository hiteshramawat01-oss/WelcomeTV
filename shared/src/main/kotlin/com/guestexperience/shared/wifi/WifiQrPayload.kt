package com.guestexperience.shared.wifi

import com.guestexperience.shared.model.WifiInfo
import com.guestexperience.shared.model.WifiSecurity

/**
 * Builds and parses the de-facto standard Wi-Fi QR payload understood by Android and iOS cameras:
 *
 *     WIFI:T:WPA;S:<ssid>;P:<password>;H:true;;
 *
 * The characters `\ ; , : "` inside the SSID or password are escaped with a backslash.
 */
object WifiQrPayload {
    private const val SPECIAL = "\\;,:\""

    fun build(wifi: WifiInfo): String = buildString {
        append("WIFI:")
        append("T:").append(wifi.security.qrToken).append(';')
        append("S:").append(escape(wifi.ssid)).append(';')
        if (wifi.security != WifiSecurity.OPEN) {
            append("P:").append(escape(wifi.password)).append(';')
        }
        if (wifi.hidden) append("H:true;")
        append(';')
    }

    fun escape(value: String): String = buildString(value.length + 8) {
        for (c in value) {
            if (c in SPECIAL) append('\\')
            append(c)
        }
    }

    /** Parses a payload produced by [build] (or by most QR generators). Returns null if it is not a Wi-Fi payload. */
    fun parse(payload: String): WifiInfo? {
        if (!payload.startsWith("WIFI:")) return null
        val fields = mutableMapOf<String, String>()
        var i = 5
        while (i < payload.length) {
            if (payload[i] == ';') break // the terminating ";;"
            val colon = payload.indexOf(':', i)
            if (colon < 0) return null
            val key = payload.substring(i, colon)
            val value = StringBuilder()
            var j = colon + 1
            while (j < payload.length && payload[j] != ';') {
                if (payload[j] == '\\' && j + 1 < payload.length) j++
                value.append(payload[j])
                j++
            }
            if (j >= payload.length) return null
            fields[key] = value.toString()
            i = j + 1
        }
        val security = when (fields["T"]?.uppercase()) {
            "WEP" -> WifiSecurity.WEP
            "NOPASS", "", null -> if (fields["P"].isNullOrEmpty()) WifiSecurity.OPEN else WifiSecurity.WPA
            else -> WifiSecurity.WPA
        }
        return WifiInfo(
            ssid = fields["S"] ?: return null,
            password = fields["P"].orEmpty(),
            security = security,
            hidden = fields["H"].equals("true", ignoreCase = true),
        )
    }
}
