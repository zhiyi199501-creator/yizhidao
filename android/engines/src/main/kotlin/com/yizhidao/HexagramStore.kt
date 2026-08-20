package com.yizhidao

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.InputStream

class HexagramStore(hexagrams: List<Hexagram>) {
    val hexagrams: List<Hexagram> = hexagrams.sortedBy { it.number }
    private val byNumber: Map<Int, Hexagram> = this.hexagrams.associateBy { it.number }

    fun hexagram(number: Int): Hexagram? = byNumber[number]

    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromJson(text: String): HexagramStore {
            val root = json.parseToJsonElement(text)
            val array = when (root) {
                is JsonArray -> root
                is JsonObject -> root["hexagrams"] as? JsonArray
                    ?: error("Hexagrams.json missing hexagrams array")
                else -> error("Hexagrams.json must be an array or an object with hexagrams")
            }
            val decoded = json.decodeFromJsonElement(ListSerializer(Hexagram.serializer()), array)
            return HexagramStore(decoded)
        }

        fun fromStream(stream: InputStream): HexagramStore =
            fromJson(stream.bufferedReader().use { it.readText() })
    }
}
