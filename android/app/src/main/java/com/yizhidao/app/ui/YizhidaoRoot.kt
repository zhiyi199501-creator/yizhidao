package com.yizhidao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yizhidao.CastResult
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.cases.CaseListScreen
import com.yizhidao.app.ui.casting.CastingHomeScreen
import com.yizhidao.app.ui.history.HistoryListScreen
import com.yizhidao.app.ui.history.SimilarHexagramJump
import com.yizhidao.app.ui.me.MeScreen
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.app.ui.theme.AppTheme

enum class AppTab(val label: String, val icon: ImageVector) {
    Cast("起卦", Icons.Outlined.AutoAwesome),
    History("历史", Icons.Outlined.Schedule),
    Cases("案例", Icons.AutoMirrored.Outlined.MenuBook),
    Me("我的", Icons.Outlined.AccountCircle),
}

@Composable
fun YizhidaoRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(AppTab.Cast) }
    var pendingResult by remember { mutableStateOf<CastResult?>(null) }
    var historyOpenId by remember { mutableStateOf<String?>(null) }
    var similarJumpTick by remember { mutableIntStateOf(0) }
    var similarJump by remember { mutableStateOf<SimilarHexagramJump?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            PaperTabBar(selected = tab, onSelect = { tab = it })
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .background(AppTheme.parchmentBrush)
                .padding(inner),
        ) {
            when (tab) {
                AppTab.Cast -> {
                    val result = pendingResult
                    if (result != null) {
                        ResultScreen(
                            result = result,
                            isNew = true,
                            container = container,
                            onBack = { pendingResult = null },
                            onOpenSimilar = { result ->
                                similarJump = SimilarHexagramJump.from(result)
                                similarJumpTick += 1
                                pendingResult = null
                                tab = AppTab.History
                            },
                        )
                    } else {
                        CastingHomeScreen(
                            container = container,
                            onResult = { pendingResult = it },
                        )
                    }
                }
                AppTab.History -> HistoryListScreen(
                    container = container,
                    openRecordId = historyOpenId,
                    similarJump = similarJump,
                    similarJumpTick = similarJumpTick,
                    onOpenRecord = { historyOpenId = it },
                    onCloseRecord = { historyOpenId = null },
                )
                AppTab.Cases -> CaseListScreen(container = container)
                AppTab.Me -> MeScreen(container = container)
            }
        }
    }
}

@Composable
private fun PaperTabBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTab.entries.forEach { item ->
                    val isSelected = selected == item
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AppTheme.accentSoft.copy(alpha = 0.85f) else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = if (isSelected) AppTheme.accent else AppTheme.ink.copy(alpha = 0.78f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            color = if (isSelected) AppTheme.accent else AppTheme.ink.copy(alpha = 0.78f),
                            style = AppTheme.compactText,
                        )
                    }
                }
            }
        }
    }
}
