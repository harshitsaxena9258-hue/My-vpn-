package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.NetworkDiagnosticsClient
import com.example.data.VpnPreferencesRepository
import com.example.data.VpnStateHolder
import com.example.data.VpnUserPreferences
import com.example.model.DiagnosticVerificationResult
import com.example.model.GoogleUsaServers
import com.example.model.TunnelProtocol
import com.example.model.VpnConnectionStatus
import com.example.model.VpnServer
import com.example.model.VpnTelemetry
import com.example.vpn.PulseVpnService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PulseVpnUiState(
  val telemetry: VpnTelemetry = VpnTelemetry(),
  val preferences: VpnUserPreferences = VpnUserPreferences(),
  val servers: List<VpnServer> = GoogleUsaServers.all,
  val isPingingAll: Boolean = false,
  val verification: DiagnosticVerificationResult = DiagnosticVerificationResult(),
  val elapsedSeconds: Long = 0L,
  val bannerMessage: String? = null,
) {
  val selectedServer: VpnServer
    get() =
      servers.firstOrNull { it.id == preferences.selectedServerId }
        ?: GoogleUsaServers.findById(preferences.selectedServerId)
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

  private val preferencesRepo = VpnPreferencesRepository(application)
  private val diagnosticsClient = NetworkDiagnosticsClient()

  private val _servers = MutableStateFlow(GoogleUsaServers.all)
  private val _isPingingAll = MutableStateFlow(false)
  private val _verification = MutableStateFlow(DiagnosticVerificationResult())
  private val _elapsedSeconds = MutableStateFlow(0L)
  private val _bannerMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<PulseVpnUiState> =
    combine(
        VpnStateHolder.telemetry,
        preferencesRepo.preferencesFlow,
        _servers,
        _isPingingAll,
        _verification,
        _elapsedSeconds,
        _bannerMessage,
      ) { values ->
        val telemetry = values[0] as VpnTelemetry
        val prefs = values[1] as VpnUserPreferences
        @Suppress("UNCHECKED_CAST") val serverList = values[2] as List<VpnServer>
        val pinging = values[3] as Boolean
        val verification = values[4] as DiagnosticVerificationResult
        val elapsed = values[5] as Long
        val banner = values[6] as String?

        PulseVpnUiState(
          telemetry = telemetry,
          preferences = prefs,
          servers = serverList,
          isPingingAll = pinging,
          verification = verification,
          elapsedSeconds = elapsed,
          bannerMessage = banner,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PulseVpnUiState(),
      )

  init {
    refreshAllServerPings()
    startSessionClock()
  }

  private fun startSessionClock() {
    viewModelScope.launch {
      while (true) {
        val telemetry = VpnStateHolder.telemetry.value
        val connectedSince = telemetry.connectedSinceMs
        _elapsedSeconds.value =
          if (telemetry.status == VpnConnectionStatus.CONNECTED && connectedSince != null) {
            ((System.currentTimeMillis() - connectedSince) / 1000L).coerceAtLeast(0L)
          } else {
            0L
          }
        delay(1000L)
      }
    }
  }

  fun prepareVpnIntent(): Intent? = VpnService.prepare(getApplication())

  fun startVpnConnection() {
    val context = getApplication<Application>()
    val prefs = uiState.value.preferences
    val intent =
      Intent(context, PulseVpnService::class.java).apply {
        action = PulseVpnService.ACTION_CONNECT
        putExtra(PulseVpnService.EXTRA_SERVER_ID, prefs.selectedServerId)
        putExtra(PulseVpnService.EXTRA_PROTOCOL, prefs.protocol.name)
        putExtra(PulseVpnService.EXTRA_IPV6, prefs.ipv6Enabled)
      }
    _bannerMessage.value = null
    ContextCompat.startForegroundService(context, intent)
  }

  fun stopVpnConnection() {
    val context = getApplication<Application>()
    val intent =
      Intent(context, PulseVpnService::class.java).apply {
        action = PulseVpnService.ACTION_DISCONNECT
      }
    context.startService(intent)
  }

  fun onVpnPermissionDenied(message: String) {
    _bannerMessage.value = message
  }

  fun dismissBanner() {
    _bannerMessage.value = null
  }

  fun selectServer(serverId: String) {
    viewModelScope.launch {
      preferencesRepo.setSelectedServer(serverId)
      VpnStateHolder.selectServerWhenDisconnected(serverId)
      if (uiState.value.telemetry.status == VpnConnectionStatus.CONNECTED) {
        val context = getApplication<Application>()
        val prefs = uiState.value.preferences
        val intent =
          Intent(context, PulseVpnService::class.java).apply {
            action = PulseVpnService.ACTION_CONNECT
            putExtra(PulseVpnService.EXTRA_SERVER_ID, serverId)
            putExtra(PulseVpnService.EXTRA_PROTOCOL, prefs.protocol.name)
            putExtra(PulseVpnService.EXTRA_IPV6, prefs.ipv6Enabled)
          }
        ContextCompat.startForegroundService(context, intent)
      }
    }
  }

  fun setProtocol(protocol: TunnelProtocol) {
    viewModelScope.launch {
      preferencesRepo.setProtocol(protocol)
      if (uiState.value.telemetry.status == VpnConnectionStatus.CONNECTED) {
        val context = getApplication<Application>()
        val prefs = uiState.value.preferences
        val intent =
          Intent(context, PulseVpnService::class.java).apply {
            action = PulseVpnService.ACTION_CONNECT
            putExtra(PulseVpnService.EXTRA_SERVER_ID, prefs.selectedServerId)
            putExtra(PulseVpnService.EXTRA_PROTOCOL, protocol.name)
            putExtra(PulseVpnService.EXTRA_IPV6, prefs.ipv6Enabled)
          }
        ContextCompat.startForegroundService(context, intent)
      }
    }
  }

  fun setIpv6Enabled(enabled: Boolean) {
    viewModelScope.launch {
      preferencesRepo.setIpv6Enabled(enabled)
      if (uiState.value.telemetry.status == VpnConnectionStatus.CONNECTED) {
        startVpnConnection()
      }
    }
  }

  fun refreshAllServerPings() {
    if (_isPingingAll.value) return
    viewModelScope.launch {
      _isPingingAll.value = true
      _servers.update { list -> list.map { it.copy(isCheckingPing = true) } }

      val updated =
        _servers.value
          .map { server ->
            async {
              val ping = diagnosticsClient.measureServerPing(server)
              server.copy(pingMs = ping, isCheckingPing = false)
            }
          }
          .awaitAll()

      _servers.value = updated
      _isPingingAll.value = false
    }
  }

  fun verifyGoogleDnsAndIp() {
    if (_verification.value.isVerifying) return
    viewModelScope.launch {
      _verification.update { it.copy(isVerifying = true, message = null) }
      _verification.value = diagnosticsClient.verifyGoogleDnsAndPublicIp()
    }
  }

  fun clearDnsLogs() {
    VpnStateHolder.clearLogs()
  }
}
