package com.yizhidao.app.ima

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ImaExplanationEntry(
    val title: String,
    val scripture: String,
    val answer: String,
)

@Serializable
private data class ImaExplanationsFile(
    val version: Int = 1,
    val source: String = "",
    val entries: Map<String, ImaExplanationEntry> = emptyMap(),
)

object ImaExplanationId {
    fun guaci(number: Int): String = "%02d-guaci".format(number)
    fun tuanci(number: Int): String = "%02d-tuanci".format(number)
    fun daxiang(number: Int): String = "%02d-daxiang".format(number)

    /** `position` 为 1…6（初爻=1） */
    fun yaoPair(number: Int, position: Int): String =
        "%02d-yao-%d".format(number, position - 1)
}

class ImaExplanationStore(jsonText: String) {
    private val file: ImaExplanationsFile

    val source: String
        get() = file.source

    init {
        val json = Json { ignoreUnknownKeys = true }
        file = json.decodeFromString(jsonText)
    }

    fun explanation(id: String): ImaExplanationEntry? = file.entries[id]

    fun hasExplanation(id: String): Boolean = file.entries.containsKey(id)
}
