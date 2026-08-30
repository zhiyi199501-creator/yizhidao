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
data class YijingIntroLink(
    val title: String,
    val subtitle: String = "",
    val route: String,
)

@Serializable
data class YijingIntroBlock(
    val type: String,
    val text: String = "",
    val cite: String = "",
    val kind: String = "",
    val caption: String = "",
    val items: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val links: List<YijingIntroLink> = emptyList(),
) {
    val plainText: String
        get() = when (type) {
            "p", "quote" -> listOf(text, cite).filter { it.isNotBlank() }.joinToString(" ")
            "list" -> items.joinToString(" ")
            "table" -> rows.flatten().joinToString(" ")
            "figure" -> caption
            "links" -> links.joinToString(" ") { "${it.title} ${it.subtitle}" }
            else -> text
        }
}

@Serializable
data class YijingIntroChapter(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val blocks: List<YijingIntroBlock> = emptyList(),
) {
    val plainText: String get() = blocks.joinToString(" ") { it.plainText }
}

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
