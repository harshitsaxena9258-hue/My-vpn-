package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.VpnConnectionStatus

enum class PulseTab(
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  VPN(R.string.nav_vpn, Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_tab_vpn"),
  SERVERS(R.string.nav_servers, Icons.Rounded.Public, Icons.Outlined.Public, "nav_tab_servers"),
  DIAGNOSTICS(
    R.string.nav_diagnostics,
    Icons.Rounded.Analytics,
    Icons.Outlined.Analytics,
    "nav_tab_diagnostics",
  ),
}

@Composable
fun PulseVpnApp(vpnViewModel: VpnViewModel = viewModel()) {
  val uiState by vpnViewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var currentTab by rememberSaveable { mutableStateOf(PulseTab.VPN) }

  val deniedMsg = stringResource(R.string.vpn_permission_denied)

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
      // Foreground VPN service works even if notification permission is declined.
    }

  val vpnPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
              PackageManager.PERMISSION_GRANTED
        ) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vpnViewModel.startVpnConnection()
      } else {
        vpnViewModel.onVpnPermissionDenied(deniedMsg)
      }
    }

  val handleConnectToggle: () -> Unit = {
    val currentStatus = uiState.telemetry.status
    if (
      currentStatus == VpnConnectionStatus.CONNECTED ||
        currentStatus == VpnConnectionStatus.CONNECTING
    ) {
      vpnViewModel.stopVpnConnection()
    } else {
      val prepareIntent = vpnViewModel.prepareVpnIntent()
      if (prepareIntent != null) {
        vpnPermissionLauncher.launch(prepareIntent)
      } else {
        if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
              PackageManager.PERMISSION_GRANTED
        ) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vpnViewModel.startVpnConnection()
      }
    }
  }

  BackHandler(enabled = currentTab != PulseTab.VPN) { currentTab = PulseTab.VPN }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets.navigationBars,
          ) {
            PulseTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(text = stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            PulseTab.entries.forEach { tab ->
              val selected = currentTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(text = stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        when (currentTab) {
          PulseTab.VPN ->
            ConnectScreen(
              uiState = uiState,
              onConnectToggle = handleConnectToggle,
              onOpenServers = { currentTab = PulseTab.SERVERS },
              onDismissBanner = { vpnViewModel.dismissBanner() },
            )
          PulseTab.SERVERS ->
            ServersScreen(
              uiState = uiState,
              onSelectServer = { serverId ->
                vpnViewModel.selectServer(serverId)
                currentTab = PulseTab.VPN
              },
              onRefreshPings = { vpnViewModel.refreshAllServerPings() },
            )
          PulseTab.DIAGNOSTICS ->
            DiagnosticsScreen(
              uiState = uiState,
              onProtocolSelected = { vpnViewModel.setProtocol(it) },
              onIpv6Toggled = { vpnViewModel.setIpv6Enabled(it) },
              onVerifyGoogleDns = { vpnViewModel.verifyGoogleDnsAndIp() },
              onClearLogs = { vpnViewModel.clearDnsLogs() },
            )
        }
      }
    }
  }
}
