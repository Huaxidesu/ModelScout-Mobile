package com.modelscout.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.modelscout.mobile.core.model.Capability
import com.modelscout.mobile.core.model.LimitSource
import com.modelscout.mobile.ui.theme.CapabilityColors

@Composable
fun CapabilityIcon(
    capability: Capability,
    label: String,
    modifier: Modifier = Modifier,
) {
    val (icon, color) = when (capability) {
        Capability.SUPPORTED -> Icons.Filled.CheckCircle to CapabilityColors.Supported
        Capability.UNSUPPORTED -> Icons.Filled.Cancel to CapabilityColors.Unsupported
        Capability.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline to CapabilityColors.Unknown
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LimitBadge(source: LimitSource?, modifier: Modifier = Modifier) {
    val label = source?.label ?: "未知"
    val color = when (source) {
        LimitSource.MEASURED -> CapabilityColors.Supported
        LimitSource.DICTIONARY -> MaterialTheme.colorScheme.primary
        LimitSource.DECLARED -> MaterialTheme.colorScheme.secondary
        LimitSource.MANUAL -> CapabilityColors.Unknown
        null -> MaterialTheme.colorScheme.outline
    }
    Text(
        text = label,
        fontSize = 10.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                trailing?.invoke()
            }
            content()
        }
    }
}
