package com.yizhidao

enum class Trigram(val number: Int, val displayName: String) {
    QIAN(1, "乾"),
    DUI(2, "兑"),
    LI(3, "离"),
    ZHEN(4, "震"),
    XUN(5, "巽"),
    KAN(6, "坎"),
    GEN(7, "艮"),
    KUN(8, "坤");

    /** Bottom → top, yang = 1. */
    val bits: List<Int>
        get() = when (this) {
            QIAN -> listOf(1, 1, 1)
            DUI -> listOf(1, 1, 0)
            LI -> listOf(1, 0, 1)
            ZHEN -> listOf(1, 0, 0)
            XUN -> listOf(0, 1, 1)
            KAN -> listOf(0, 1, 0)
            GEN -> listOf(0, 0, 1)
            KUN -> listOf(0, 0, 0)
        }

    companion object {
        fun fromMod8(value: Int): Trigram {
            val r = ((value % 8) + 8) % 8
            val n = if (r == 0) 8 else r
            return entries.find { it.number == n } ?: KUN
        }
    }
}

object KingWenTable {
    /** King Wen binaries bottom→top (yang=1), index 0 = hexagram #1 乾. */
    private val orderedBinaries: List<String> = listOf(
        "111111", "000000", "100010", "010001",
        "111010", "010111", "010000", "000010",
        "111011", "110111", "111000", "000111",
        "101111", "111101", "001000", "000100",
        "100110", "011001", "110000", "000011",
        "100101", "101001", "000001", "100000",
        "100111", "111001", "100001", "011110",
        "010010", "101101", "001110", "011100",
        "001111", "111100", "000101", "101000",
        "101011", "110101", "001010", "010100",
        "110001", "100011", "111110", "011111",
        "000110", "011000", "010110", "011010",
        "101110", "011101", "100100", "001001",
        "001011", "110100", "101100", "001101",
        "011011", "110110", "010011", "110010",
        "110011", "001100", "101010", "010101",
    )

    private val binaryToNumber: Map<String, Int> =
        orderedBinaries.mapIndexed { idx, binary -> binary to (idx + 1) }.toMap()

    fun number(fromBits: List<Int>): Int {
        require(fromBits.size == 6)
        val key = fromBits.joinToString("")
        return binaryToNumber[key] ?: error("Unknown hexagram binary $key")
    }

    fun number(lower: Trigram, upper: Trigram): Int =
        number(fromBits = lower.bits + upper.bits)

    fun bits(ofNumber: Int): List<Int> {
        require(ofNumber in 1..64)
        return orderedBinaries[ofNumber - 1].map { it.digitToInt() }
    }

    fun binary(ofNumber: Int): String {
        require(ofNumber in 1..64)
        return orderedBinaries[ofNumber - 1]
    }

    /** Flip changing positions (1-based) → resulting King Wen number; null if none. */
    fun resultingNumber(primaryBits: List<Int>, movingPositions: List<Int>): Int? {
        if (movingPositions.isEmpty()) return null
        val bits = primaryBits.toMutableList()
        for (p in movingPositions) {
            val i = p - 1
            if (i in 0..5) {
                bits[i] = if (bits[i] == 1) 0 else 1
            }
        }
        return number(fromBits = bits)
    }
}
