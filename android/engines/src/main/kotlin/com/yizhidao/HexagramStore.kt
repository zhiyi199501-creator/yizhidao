package com.yizhidao

import kotlinx.serialization.json.Json
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
            val decoded = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Hexagram.serializer()), text)
            return HexagramStore(decoded)
        }

        fun fromStream(stream: InputStream): HexagramStore =
            fromJson(stream.bufferedReader().use { it.readText() })
    }
}
