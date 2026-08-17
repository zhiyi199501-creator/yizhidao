package com.yizhidao

import java.security.SecureRandom
import java.time.Instant

fun interface RandomSource {
    fun nextBoolean(): Boolean
}

class SecureRandomSource(
    private val rng: SecureRandom = SecureRandom(),
) : RandomSource {
    override fun nextBoolean(): Boolean = rng.nextBoolean()

    fun nextInt(bound: Int): Int = rng.nextInt(bound)

    /** Inclusive range, matching Swift `Int.random(in:)`. */
    fun nextInt(range: IntRange): Int {
        val size = range.last - range.first + 1
        require(size > 0)
        return range.first + rng.nextInt(size)
    }
}

object CoinCastingEngine {
    /** Character side (字) = yang 3; Manchu/back (背) = yin 2. */
    fun line(fromYangCount: Int): LineValue = when (fromYangCount) {
        3 -> LineValue.OLD_YANG
        2 -> LineValue.YOUNG_YIN
        1 -> LineValue.YOUNG_YANG
        0 -> LineValue.OLD_YIN
        else -> error("yangCount must be 0...3")
    }

    fun tossLine(rng: RandomSource): LineValue {
        var yang = 0
        repeat(3) {
            if (rng.nextBoolean()) yang += 1
        }
        return line(fromYangCount = yang)
    }

    fun tossLine(): LineValue = tossLine(SecureRandomSource())

    /** Six tosses bottom → top. */
    fun cast(
        lines: List<LineValue>,
        question: String? = null,
        at: Instant = Instant.now(),
    ): CastResult {
        require(lines.size == 6)
        val bits = lines.map { it.bit }
        val primary = KingWenTable.number(fromBits = bits)
        val moving = lines.mapIndexedNotNull { idx, line ->
            if (line.isChanging) idx + 1 else null
        }
        val resulting = KingWenTable.resultingNumber(
            primaryBits = bits,
            movingPositions = moving,
        )
        return CastResult(
            method = CastingMethod.COIN,
            createdAt = at,
            question = question,
            numbers = lines.map { it.rawValue },
            primaryNumber = primary,
            resultingNumber = resulting,
            lines = lines,
            movingPositions = moving,
        )
    }

    fun castRandom(
        question: String? = null,
        at: Instant = Instant.now(),
        rng: RandomSource = SecureRandomSource(),
    ): CastResult {
        val lines = List(6) { tossLine(rng) }
        return cast(lines = lines, question = question, at = at)
    }
}
