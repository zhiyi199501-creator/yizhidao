package com.yizhidao.app.ui.cases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yizhidao.CaseStudy
import com.yizhidao.app.AppContainer
import com.yizhidao.app.ui.reading.HexagramReadingBody
import com.yizhidao.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseListScreen(container: AppContainer) {
    val cases = remember { container.caseRepository.loadBundled() }
    var selectedHex by remember { mutableStateOf<Int?>(null) }
    var selectedCase by remember { mutableStateOf<CaseStudy?>(null) }

    when {
        selectedCase != null -> CaseDetailScreen(
            study = selectedCase!!,
            container = container,
            onBack = { selectedCase = null },
        )
        selectedHex != null -> {
            val hex = container.hexagramStore.hexagram(selectedHex!!)
            val items = cases.filter { it.number == selectedHex }
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    "← ${hex?.symbol.orEmpty()} ${hex?.name ?: ""}",
                    color = AppTheme.accent,
                    modifier = Modifier.clickable { selectedHex = null }.padding(bottom = 12.dp),
                )
                LazyColumn {
                    items(items, key = { it.file }) { study ->
                        ListItem(
                            headlineContent = { Text(study.position) },
                            supportingContent = { Text(study.question, maxLines = 2) },
                            modifier = Modifier.clickable { selectedCase = study },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
        else -> {
            val grouped = cases.groupBy { it.number }.toSortedMap()
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("案例", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Text(
                    "按文王序浏览。离线使用包内底稿。",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (number, items) ->
                        val hex = container.hexagramStore.hexagram(number)
                        item(number) {
                            ListItem(
                                headlineContent = { Text("${hex?.symbol.orEmpty()} ${hex?.name ?: number}") },
                                supportingContent = { Text("${items.size} 则") },
                                modifier = Modifier.clickable { selectedHex = number },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaseDetailScreen(study: CaseStudy, container: AppContainer, onBack: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("${study.hexagram} · ${study.position}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaseBlock("背景", study.background)
            CaseBlock("所问何事", study.question)
            CaseBlock("验证", study.verification)
            CaseBlock("讲师解读", study.explanation)
            HexagramReadingBody(
                primaryNumber = study.number,
                resultingNumber = study.resultingNumber,
                lines = study.lines,
                movingPositions = study.movingPositions,
                store = container.hexagramStore,
            )
        }
    }
}

@Composable
private fun CaseBlock(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppTheme.cardFill, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, color = AppTheme.accent)
        Text(body)
    }
}
