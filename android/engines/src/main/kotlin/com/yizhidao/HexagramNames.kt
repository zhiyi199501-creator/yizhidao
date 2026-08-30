package com.yizhidao

/** 卦名拼音与短别称。别称只帮忙认卦，不替代原文。 */
object HexagramNames {
    fun pinyin(number: Int): String = entry(number)?.first.orEmpty()

    fun epithet(number: Int): String = entry(number)?.second.orEmpty()

    private fun entry(number: Int): Pair<String, String>? {
        if (number !in 1..64) return null
        return table[number - 1]
    }

    private val table: List<Pair<String, String>> = listOf(
        "Qián" to "Heaven",
        "Kūn" to "Earth",
        "Zhūn" to "Sprouting",
        "Méng" to "Folly",
        "Xū" to "Waiting",
        "Sòng" to "Conflict",
        "Shī" to "The Army",
        "Bǐ" to "Holding Together",
        "Xiǎo Chù" to "Small Restraint",
        "Lǚ" to "Treading",
        "Tài" to "Peace",
        "Pǐ" to "Standstill",
        "Tóng Rén" to "Fellowship",
        "Dà Yǒu" to "Great Possession",
        "Qiān" to "Modesty",
        "Yù" to "Enthusiasm",
        "Suí" to "Following",
        "Gǔ" to "Decay",
        "Lín" to "Approach",
        "Guān" to "Contemplation",
        "Shì Kè" to "Biting Through",
        "Bì" to "Grace",
        "Bō" to "Splitting",
        "Fù" to "Return",
        "Wú Wàng" to "Innocence",
        "Dà Chù" to "Great Restraint",
        "Yí" to "Nourishment",
        "Dà Guò" to "Great Exceeding",
        "Kǎn" to "The Abyss",
        "Lí" to "Radiance",
        "Xián" to "Influence",
        "Héng" to "Duration",
        "Dùn" to "Retreat",
        "Dà Zhuàng" to "Great Power",
        "Jìn" to "Progress",
        "Míng Yí" to "Darkening",
        "Jiā Rén" to "The Family",
        "Kuí" to "Opposition",
        "Jiǎn" to "Obstruction",
        "Xiè" to "Deliverance",
        "Sǔn" to "Decrease",
        "Yì" to "Increase",
        "Guài" to "Breakthrough",
        "Gòu" to "Encounter",
        "Cuì" to "Gathering",
        "Shēng" to "Ascending",
        "Kùn" to "Oppression",
        "Jǐng" to "The Well",
        "Gé" to "Revolution",
        "Dǐng" to "The Cauldron",
        "Zhèn" to "Thunder",
        "Gèn" to "Mountain",
        "Jiàn" to "Gradual",
        "Guī Mèi" to "Marrying Maiden",
        "Fēng" to "Abundance",
        "Lǚ" to "The Wanderer",
        "Xùn" to "Wind",
        "Duì" to "Joy",
        "Huàn" to "Dispersion",
        "Jié" to "Limitation",
        "Zhōng Fú" to "Inner Trust",
        "Xiǎo Guò" to "Small Exceeding",
        "Jì Jì" to "After Completion",
        "Wèi Jì" to "Before Completion",
    )
}
