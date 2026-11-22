package com.omnitracker.renpho.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.omnitracker.renpho.R
import com.omnitracker.renpho.core.presentation.MetricStatus
import com.omnitracker.renpho.ui.theme.RenphoTheme
import com.omnitracker.renpho.ui.theme.color

/** UI model for one metric value. */
data class MetricValueUi(
    val label: Int,
    val value: String?,
    val status: MetricStatus?,
)

@Composable
fun MetricTile(
    metric: MetricValueUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = stringResource(metric.label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = metric.value ?: stringResource(R.string.not_available),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.height(20.dp)) {
                metric.status?.let { StatusBadge(it) }
            }
        }
    }
}

@Composable
fun StatusBadge(status: MetricStatus, modifier: Modifier = Modifier) {
    val color = status.tone.color()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(stringResource(status.label), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** A full-width row used on detail screens. */
@Composable
fun MetricRow(metric: MetricValueUi, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(metric.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        metric.status?.let {
            StatusBadge(it)
            Spacer(Modifier.size(12.dp))
        }
        Text(
            text = metric.value ?: stringResource(R.string.not_available),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview
@Composable
private fun MetricTilePreview() {
    RenphoTheme {
        MetricTile(MetricValueUi(R.string.metric_body_fat, "21.4 %", MetricStatus.HIGH))
    }
}
