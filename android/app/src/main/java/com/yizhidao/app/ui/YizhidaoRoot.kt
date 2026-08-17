package com.yizhidao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.yizhidao.CastResult
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.cases.CaseListScreen
import com.yizhidao.app.ui.casting.CastingHomeScreen
import com.yizhidao.app.ui.history.HistoryListScreen
import com.yizhidao.app.ui.me.MeScreen
import com.yizhidao.app.ui.reading.ResultScreen
import com.yizhidao.app.ui.theme.AppTheme

enum class AppTab(val label: String, val icon: ImageVector) {
    Cast("起卦", Icons.Outlined.AutoAwesome),
    History("历史", Icons.Outlined.Schedule),
    Cases("案例", Icons.Outlined.MenuBook),
    Me("我的", Icons.Outlined.Person),
}

@Composable
fun YizhidaoRoot(container: AppContainer) {
    var tab by remember { mutableStateOf(AppTab.Cast) }
    var pendingResult by remember { mutableStateOf<CastResult?>(null) }
    var historyOpenId by remember { mutableStateOf<String?>(null) }
    var similarJumpTick by remember { mutableIntStateOf(0) }
    var similarPrimary by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = AppTheme.parchmentTop.copy(alpha = 0.96f)) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppTheme.accent,
                            selectedTextColor = AppTheme.accent,
                            indicatorColor = AppTheme.accent.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
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
                            onOpenSimilar = { hex ->
                                similarPrimary = hex
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
                    similarPrimary = similarPrimary,
                    similarJumpTick = similarJumpTick,
                    onOpenRecord = { historyOpenId = it },
                    onCloseRecord = { historyOpenId = null },
                )
                AppTab.Cases -> CaseListScreen(container = container)
                AppTab.Me -> MeScreen()
            }
        }
    }
}
