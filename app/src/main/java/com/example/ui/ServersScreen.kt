package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VpnServer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MonoTextStyle

@Composable
fun ServersScreen(
  uiState: PulseVpnUiState,
  onSelectServer: (String) -> Unit,
  onRefreshPings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Google USA Servers",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "All endpoints are 100% free • Anycast IPv4/IPv6",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          FilledTonalButton(
            onClick = onRefreshPings,
            enabled = !uiState.isPingingAll,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("ping_all_button"),
          ) {
            if (uiState.isPingingAll) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.action_ping_all))
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
      }

      items(uiState.servers, key = { it.id }) { server ->
        val isSelected = server.id == uiState.preferences.selectedServerId
        ServerOptionCard(
          server = server,
          isSelected = isSelected,
          onClick = { onSelectServer(server.id) },
        )
      }
    }
  }
}

@Composable
private fun ServerOptionCard(
  server: VpnServer,
  isSelected: Boolean,
  onClick: () -> Unit,
) {
  val borderColor =
    if (isSelected) EmeraldShield else MaterialTheme.colorScheme.outline

  Card(
    modifier =
      Modifier
        .fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onClick)
        .testTag("server_card_${server.id}"),
    shape = RoundedCornerShape(20.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isSelected) {
            EmeraldShield.copy(alpha = 0.09f)
          } else {
            MaterialTheme.colorScheme.surface
          }
      ),
    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = CircleShape,
          color =
            if (isSelected) EmeraldShield.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Public,
              contentDescription = null,
              tint = if (isSelected) EmeraldBright else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp),
            )
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = server.name,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = ElectricCyan.copy(alpha = 0.14f),
            ) {
              Text(
                text = server.badge,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = server.region,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (server.isCheckingPing) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = ElectricCyan,
          )
        } else {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = EmeraldShield.copy(alpha = 0.14f),
          ) {
            Text(
              text = server.pingMs?.let { "${it} ms" } ?: "-- ms",
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldBright,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        IpChip(label = "IPv4", value = "${server.primaryIp} / ${server.secondaryIp}", modifier = Modifier.weight(1f))
        IpChip(label = "Free Tier", value = "Unlimited", modifier = Modifier)
      }
    }
  }
}

@Composable
private fun IpChip(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "$label: ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MonoTextStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
