package com.yizhidao.app.ui.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MeScreen() {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("我的", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "登录、AI 解读与回收站将在后续接入。起卦、历史与案例已可离线使用。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}
