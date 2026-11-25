package com.guestexperience.shared.model

import kotlinx.serialization.Serializable

/**
 * Everything the guest screen shows. This is the single JSON document that is stored on the TV,
 * sent from the Host phone app, and (in a later version) synced through the cloud.
 *
 * Rules for evolving it:
 *  - Only ever ADD fields, always with a default value.
 *  - Never rename or re-purpose a field; bump [schemaVersion] and migrate in [ContentJson] instead.
 */
@Serializable
data class PropertyContent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val propertyName: String = "",
    val location: String = "",
    val guestName: String = "",
    val hostMessage: String = "",
    val wifi: WifiInfo = WifiInfo(),
    val checkOut: CheckOut? = null,
    val houseNotes: List<String> = emptyList(),
    val places: List<Place> = emptyList(),
    val hostContact: HostContact? = null,
    val background: BackgroundChoice = BackgroundChoice(),
    val timeFormat: TimeFormat = TimeFormat.H12,
    /** Epoch millis of the last edit; used to show "last updated" and for future sync. */
    val updatedAt: Long = 0L,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class WifiInfo(
    val ssid: String = "",
    val password: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val hidden: Boolean = false,
)

@Serializable
enum class WifiSecurity(val qrToken: String) {
    /** WPA, WPA2 and WPA3-transition networks; the token every phone camera understands. */
    WPA("WPA"),
    WEP("WEP"),
    OPEN("nopass"),
}

/** ISO-8601 local date (`2026-11-26`) and 24-hour time (`11:00`), kept as text so the JSON stays portable. */
@Serializable
data class CheckOut(
    val date: String,
    val time: String,
)

@Serializable
data class Place(
    val name: String,
    val description: String = "",
)

@Serializable
data class HostContact(
    val name: String = "",
    val phone: String = "",
    val note: String = "",
)

@Serializable
data class BackgroundChoice(
    /** Built-in scene id; see [BuiltInScene]. Unknown ids fall back to the default scene. */
    val scene: String = BuiltInScene.UDAIPUR_DUSK.id,
    /** True when the host picked a photo stored on the TV. The file itself never leaves the TV. */
    val useCustomPhoto: Boolean = false,
)

enum class BuiltInScene(val id: String) {
    UDAIPUR_DUSK("udaipur_dusk"),
    UDAIPUR_NIGHT("udaipur_night"),
    PLAIN("plain");

    companion object {
        fun fromId(id: String): BuiltInScene = entries.firstOrNull { it.id == id } ?: UDAIPUR_DUSK
    }
}

@Serializable
enum class TimeFormat { H12, H24 }
