package com.yizhidao

import java.time.Instant

object DigitalCastingEngine {
    /** Remainder rules from lecture notes: mod8→0 means 坤(8); mod6→0 means 上爻(6). */
    fun mod8(value: Int): Int {
        val r = ((value % 8) + 8) % 8
        return if (r == 0) 8 else r
    }

    fun mod6(value: Int): Int {
        val r = ((value % 6) + 6) % 6
        return if (r == 0) 6 else r
    }

    /** Three-number method: n1 upper, n2 lower, n3 moving line (1...6). */
    fun cast(
        number1: Int,
        number2: Int,
        number3: Int,
        question: String? = null,
        at: Instant = Instant.now(),
    ): CastResult {
        val upper = Trigram.fromMod8(number1)
        val lower = Trigram.fromMod8(number2)
        val moving = mod6(number3)
        return buildResult(
            lower = lower,
            upper = upper,
            movingPosition = moving,
            method = CastingMethod.DIGITAL_MANUAL,
            numbers = listOf(number1, number2, number3),
            question = question,
            date = at,
        )
    }

    /** Time method: yearBranch / month / day / hourBranch 均为 1...12（时为时辰）. */
    fun castTime(
        yearBranch: Int,
        month: Int,
        day: Int,
        hour: Int,
        question: String? = null,
        at: Instant = Instant.now(),
    ): CastResult {
        val upperSum = yearBranch + month + day
        val total = upperSum + hour
        val upper = Trigram.fromMod8(upperSum)
        val lower = Trigram.fromMod8(total)
        val moving = mod6(total)
        return buildResult(
            lower = lower,
            upper = upper,
            movingPosition = moving,
            method = CastingMethod.DIGITAL_TIME,
            numbers = listOf(yearBranch, month, day, hour),
            question = question,
            date = at,
        )
    }

    private fun buildResult(
        lower: Trigram,
        upper: Trigram,
        movingPosition: Int,
        method: CastingMethod,
        numbers: List<Int>,
        question: String?,
        date: Instant,
    ): CastResult {
        val bits = lower.bits + upper.bits
        val primary = KingWenTable.number(fromBits = bits)
        val lines = bits.mapIndexed { index, bit ->
            LineValue.from(isYang = bit == 1, changing = index == movingPosition - 1)
        }
        val resulting = KingWenTable.resultingNumber(
            primaryBits = bits,
            movingPositions = listOf(movingPosition),
        )
        return CastResult(
            method = method,
            createdAt = date,
            question = question,
            numbers = numbers,
            primaryNumber = primary,
            resultingNumber = resulting,
            lines = lines,
            movingPositions = listOf(movingPosition),
        )
    }
}
