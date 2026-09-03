package com.yizhidao.app.ui.casting

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yizhidao.CastResult
import com.yizhidao.ChineseDateSource
import com.yizhidao.CoinCastingEngine
import com.yizhidao.DigitalCastingEngine
import com.yizhidao.HexagramStore
import com.yizhidao.LunarCalendarHelper
import com.yizhidao.app.ui.theme.AppTheme
import java.time.Instant
import java.time.ZonedDateTime

private enum class ActStage { Still, Invoke, Pick, Draw, Reveal }

@Composable
fun CastingActOverlay(
    chinese: ChineseDateSource,
    store: HexagramStore,
    onFinish: (CastResult) -> Unit,
    onCancel: () -> Unit,
) {
    var stage by remember { mutableStateOf(ActStage.Still) }
    var question by remember { mutableStateOf<String?>(null) }
    var intent by remember { mutableStateOf<CastingIntent?>(null) }
    var revealResult by remember { mutableStateOf<CastResult?>(null) }

    BackHandler(onBack = onCancel)
    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.parchmentBrush)
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = Triple(stage, intent, revealResult),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "castingAct",
            modifier = Modifier.fillMaxSize(),
        ) { (current, chosen, result) ->
            val asked = question
            when {
                result != null -> CastRevealAct(
                    result = result,
                    store = store,
                    onFinish = { onFinish(result) },
                )
                current == ActStage.Draw && asked != null && chosen != null -> DrawStage(
                    intent = chosen,
                    question = asked,
                    onReveal = { revealResult = it; stage = ActStage.Reveal },
                    onCancel = onCancel,
                )
                current == ActStage.Pick && asked != null -> MethodPickAct(
                    question = asked,
                    onPick = { pick ->
                        when (pick) {
                            CastingIntent.Coin, CastingIntent.DigitalNumbers -> {
                                intent = pick
                                stage = ActStage.Draw
                            }
                            CastingIntent.DigitalTime -> {
                                revealResult = castTimeNow(asked, chinese)
                                stage = ActStage.Reveal
                            }
                        }
                    },
                    onCancel = onCancel,
                )
                current == ActStage.Invoke -> InvocationAct(
                    onConfirm = { askedQuestion ->
                        question = askedQuestion
                        stage = ActStage.Pick
                    },
                    onCancel = onCancel,
                )
                else -> StillnessAct(
                    onReady = { stage = ActStage.Invoke },
                    onCancel = onCancel,
                )
            }
        }
    }
}

@Composable
private fun DrawStage(
    intent: CastingIntent,
    question: String,
    onReveal: (CastResult) -> Unit,
    onCancel: () -> Unit,
) {
    when (intent) {
        CastingIntent.Coin -> CoinTossAct(
            question = question,
            onComplete = { lines ->
                onReveal(CoinCastingEngine.cast(lines, question, Instant.now()))
            },
            onCancel = onCancel,
        )
        CastingIntent.DigitalNumbers -> NumberDrawAct(
            question = question,
            onComplete = { first, second, third ->
                onReveal(DigitalCastingEngine.cast(first, second, third, question))
            },
            onCancel = onCancel,
        )
        CastingIntent.DigitalTime -> Unit
    }
}

/** 时间卦只占此刻、只用十二时辰；点「时间起卦」那一下才取 now。 */
private fun castTimeNow(asked: String, chinese: ChineseDateSource): CastResult {
    val date = ZonedDateTime.now()
    val comps = LunarCalendarHelper.components(date, chinese)
    return DigitalCastingEngine.castTime(
        yearBranch = comps.yearBranch,
        month = comps.month,
        day = comps.day,
        hour = comps.hourBranch,
        question = asked,
        at = date.toInstant(),
    )
}
