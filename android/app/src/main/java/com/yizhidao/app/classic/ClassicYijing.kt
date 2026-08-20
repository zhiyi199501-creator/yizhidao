package com.yizhidao.app.classic

import com.yizhidao.Hexagram
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ClassicChapter(
    val title: String,
    val paragraphs: List<String>,
)

@Serializable
data class ClassicWing(
    val id: String,
    val title: String,
    val chapters: List<ClassicChapter>,
)

@Serializable
data class HexagramsBook(
    val source: String = "",
    val hexagrams: List<Hexagram> = emptyList(),
    val wings: List<ClassicWing> = emptyList(),
)

object ClassicYijingCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): HexagramsBook = json.decodeFromString(text)
}

@Serializable
data class YijingIntroChapter(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val paragraphs: List<String>,
)

@Serializable
data class YijingIntroBook(
    val source: String = "",
    val note: String = "",
    val chapters: List<YijingIntroChapter>,
)

object YijingIntroCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): YijingIntroBook = json.decodeFromString(text)
}
