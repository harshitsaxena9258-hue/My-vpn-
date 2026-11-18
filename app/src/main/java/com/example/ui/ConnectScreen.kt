package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.VpnConnectionStatus
import com.example.ui.theme.AmberPulse
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.MonoTextStyle
import java.util.Locale

@Composable
fun ConnectScreen(
  uiState: PulseVpnUiState,
  onConnectToggle: () -> Unit,
  onOpenServers: () -> Unit,
  onDismissBanner: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val status = uiState.telemetry.status
  val server = uiState.selectedServer
  val scrollState = rememberScrollState()

  val accentColor by
    animateColorAsState(
      targetValue =
        when (status) {
          VpnConnectionStatus.CONNECTED -> EmeraldShield
          VpnConnectionStatus.CONNECTING,
          VpnConnectionStatus.DISCONNECTING -> AmberPulse
          VpnConnectionStatus.ERROR -> CrimsonAlert
          VpnConnectionStatus.DISCONNECTED -> ElectricIndigo
        },
      animationSpec = tween(400),
      label = "accent_color",
    )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors =
              listOf(
                accentColor.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.background,
              ),
          )
        ),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier
          .widthIn(max = 600.dp)
          .fillMaxSize()
          .verticalScroll(scrollState)
          .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top Header & Free Forever Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(8.dp).size(22.dp),
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              text = "Google USA Encrypted DNS",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(999.dp),
          color = EmeraldShield.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, EmeraldShield.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.VerifiedUser,
              contentDescription = null,
              tint = EmeraldBright,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = "FREE • NO ADS",
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldBright,
            )
          }
        }
      }

      // Error / permission banner if any
      val errorOrBanner = uiState.bannerMessage ?: uiState.telemetry.errorMessage
      if (!errorOrBanner.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = CrimsonAlert.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.4f)),
          modifier =
            Modifier.fillMaxWidth().clickable { onDismissBanner() }.testTag("error_banner"),
        ) {
          Text(
            text = errorOrBanner,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(26.dp))

      // Live Status Pill
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier.testTag("connection_status_pill"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              when (status) {
                VpnConnectionStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
                VpnConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
                VpnConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
                VpnConnectionStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
                VpnConnectionStatus.ERROR -> stringResource(R.string.status_error)
              },
            style = MaterialTheme.typography.labelLarge,
            color = accentColor,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Session Timer / Subtitle
      AnimatedContent(
        targetState = status == VpnConnectionStatus.CONNECTED,
        label = "session_timer",
      ) { isConnected ->
        if (isConnected) {
          Text(
            text = formatDuration(uiState.elapsedSeconds),
            style =
              MaterialTheme.typography.displaySmall.copy(
                fontFamily = MonoTextStyle.fontFamily,
                fontWeight = FontWeight.Bold,
              ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag("session_timer_text"),
          )
        } else {
          Text(
            text = "Tap below to secure your connection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      // HERO CONNECT BUTTON WITH CONCENTRIC PULSE RINGS
      HeroConnectOrb(
        status = status,
        accentColor = accentColor,
        onClick = onConnectToggle,
      )

      Spacer(modifier = Modifier.height(26.dp))

      // Selected Google USA Server Card (Tappable to open Servers tab)
      Card(
        modifier =
          Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenServers)
            .testTag("selected_server_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(46.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.label_selected_server),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = server.name,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = "IP: ${server.primaryIp} • ${server.secondaryIp}",
              style = MonoTextStyle,
              color = ElectricCyan,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Column(horizontalAlignment = Alignment.End) {
            val pingText = server.pingMs?.let { "${it} ms" } ?: "-- ms"
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = EmeraldShield.copy(alpha = 0.14f),
            ) {
              Text(
                text = pingText,
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldBright,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
              contentDescription = stringResource(R.string.action_change_server),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Live Telemetry Cards Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        TelemetryMiniCard(
          icon = Icons.Rounded.Lock,
          label = stringResource(R.string.label_dns_encrypted),
          value = uiState.telemetry.queriesResolved.toString(),
          subtext = uiState.preferences.protocol.shortName,
          accent = ElectricCyan,
          modifier = Modifier.weight(1f).testTag("telemetry_queries_card"),
        )
        TelemetryMiniCard(
          icon = Icons.Rounded.Speed,
          label = stringResource(R.string.label_data_routed),
          value = formatBytes(uiState.telemetry.bytesIn + uiState.telemetry.bytesOut),
          subtext =
            uiState.telemetry.lastLatencyMs?.let { "DoH ${it}ms" }
              ?: (server.pingMs?.let { "Ping ${it}ms" } ?: "Google USA"),
          accent = EmeraldBright,
          modifier = Modifier.weight(1f).testTag("telemetry_traffic_card"),
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Compact Architecture / Privacy Footer Card
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text =
              "Fast & Private: Encrypted DNS queries route via Google USA (8.8.8.8 / 8.8.4.4) with zero bandwidth throttling, zero ads, and no paid plans.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun HeroConnectOrb(
  status: VpnConnectionStatus,
  accentColor: Color,
  onClick: () -> Unit,
) {
  val isConnected = status == VpnConnectionStatus.CONNECTED
  val isConnecting =
    status == VpnConnectionStatus.CONNECTING || status == VpnConnectionStatus.DISCONNECTING

  val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
  val pulseProgress by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = if (isConnecting) 1200 else 2200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "pulse_progress",
    )

  val orbScale by
    animateFloatAsState(
      targetValue = if (isConnected) 1.04f else 1f,
      animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
      label = "orb_scale",
    )

  val buttonLabel =
    when (status) {
      VpnConnectionStatus.CONNECTED -> stringResource(R.string.action_disconnect)
      VpnConnectionStatus.CONNECTING -> stringResource(R.string.action_connecting)
      VpnConnectionStatus.DISCONNECTING -> stringResource(R.string.status_disconnecting)
      VpnConnectionStatus.DISCONNECTED,
      VpnConnectionStatus.ERROR -> stringResource(R.string.action_connect)
    }

  Box(
    modifier = Modifier.size(236.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Animated concentric shield rings
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = this.center
      val baseRadius = size.minDimension * 0.34f
      val maxExtra = size.minDimension * 0.15f

      if (isConnected || isConnecting) {
        val ring1Radius = baseRadius + maxExtra * pulseProgress
        val ring1Alpha = (1f - pulseProgress) * 0.45f
        drawCircle(
          color = accentColor.copy(alpha = ring1Alpha),
          radius = ring1Radius,
          center = center,
          style = Stroke(width = 3.dp.toPx()),
        )

        val phase2 = (pulseProgress + 0.5f) % 1f
        val ring2Radius = baseRadius + maxExtra * phase2
        val ring2Alpha = (1f - phase2) * 0.35f
        drawCircle(
          color = accentColor.copy(alpha = ring2Alpha),
          radius = ring2Radius,
          center = center,
          style = Stroke(width = 2.dp.toPx()),
        )
      }

      // Outer static subtle track ring
      drawCircle(
        color = accentColor.copy(alpha = 0.22f),
        radius = size.minDimension * 0.42f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
      )

      if (isConnecting) {
        drawArc(
          color = accentColor,
          startAngle = pulseProgress * 360f,
          sweepAngle = 95f,
          useCenter = false,
          style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
      }
    }

    // Core Interactive Connect Button
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = if (isConnected) 14.dp else 6.dp,
      border = BorderStroke(2.5.dp, accentColor),
      modifier =
        Modifier
          .size(164.dp)
          .scale(orbScale)
          .clip(CircleShape)
          .clickable(
            enabled = !isConnecting,
            role = Role.Button,
            onClick = onClick,
          )
          .semantics { contentDescription = buttonLabel }
          .testTag("connect_button"),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors =
                  listOf(
                    accentColor.copy(alpha = if (isConnected) 0.24f else 0.14f),
                    MaterialTheme.colorScheme.surface,
                  ),
              )
            ),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(48.dp),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = buttonLabel.uppercase(Locale.US),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryMiniCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  value: String,
  subtext: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = subtext,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun formatDuration(totalSeconds: Long): String {
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format(Locale.US, "%.2f MB", mb)
}
