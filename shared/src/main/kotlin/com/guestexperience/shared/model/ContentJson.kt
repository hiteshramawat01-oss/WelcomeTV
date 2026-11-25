package com.guestexperience.shared.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The one JSON configuration used everywhere content is stored or sent.
 *
 * Forward compatibility: a newer app (or a future cloud service) may write fields this version does
 * not know about, or enum values it has never seen. Those are ignored or replaced by defaults instead
 * of crashing.
 */
object ContentJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(content: PropertyContent): String = json.encodeToString(PropertyContent.serializer(), content)

    /** Decodes and migrates older documents. Throws [IllegalArgumentException] if the text is not a content object. */
    fun decode(text: String): PropertyContent {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not valid JSON", e)
        }
        val obj = element as? JsonObject ?: throw IllegalArgumentException("Content must be a JSON object")
        val migrated = migrate(obj)
        return try {
            json.decodeFromJsonElement(PropertyContent.serializer(), migrated)
        } catch (e: Exception) {
            throw IllegalArgumentException("Content does not match the schema", e)
        }
    }

    private fun migrate(obj: JsonObject): JsonObject {
        val version = (obj["schemaVersion"] as? JsonPrimitive)?.intOrNull ?: 1
        // Version 1 is the first schema. Future migrations go here, e.g. `if (version < 2) ...`.
        return if (version < 1) JsonObject(obj + ("schemaVersion" to JsonPrimitive(1))).jsonObject else obj
    }
}
