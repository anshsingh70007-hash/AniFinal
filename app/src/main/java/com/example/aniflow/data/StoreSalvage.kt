package com.example.aniflow.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Recovers what it can from a list blob that failed to decode. Element by element first, so one bad
 * entry (or an entry with nested objects) costs only itself; the flat-object regex is only for text
 * that is no longer a JSON array at all (truncated write).
 */
internal inline fun <reified T> salvageList(json: Json, raw: String): List<T> {
    val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
    if (array != null) return array.mapNotNull { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }
    val out = ArrayList<T>()
    val matcher = java.util.regex.Pattern.compile("\\{[^{}]+\\}").matcher(raw)
    while (matcher.find()) runCatching { json.decodeFromString<T>(matcher.group()) }.getOrNull()?.let(out::add)
    return out
}
