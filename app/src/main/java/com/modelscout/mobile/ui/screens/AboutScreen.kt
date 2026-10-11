package com.modelscout.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.R
import com.modelscout.mobile.ui.AppInfo
import com.modelscout.mobile.ui.components.SectionCard

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "应用") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Unspecified,
                    modifier = Modifier.size(56.dp),
                )
                Column {
                    Text(
                        AppInfo.NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "版本 ${AppInfo.VERSION}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(AppInfo.DESCRIPTION, style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(title = "它能做什么") {
            AppInfo.HIGHLIGHTS.forEach { line ->
                Text("· $line", style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard(title = "开源") {
            Text(
                AppInfo.GITHUB_URL,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = { openUrl(context, AppInfo.GITHUB_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("  在 GitHub 上查看", modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                "欢迎提 issue 或 PR。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "声明") {
            Text(
                "本工具只读取网关公开的 /models 声明并发送最小化探测请求，" +
                    "不缓存、不上传你的 API Key（Key 仅保存在内存中，进程结束即消失）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
