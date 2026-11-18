package com.example.data

import com.example.model.DnsLogEntry
import com.example.model.GoogleUsaServers
import com.example.model.TunnelProtocol
import com.example.model.VpnConnectionStatus
import com.example.model.VpnTelemetry
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VpnStateHolder {
  private val _telemetry = MutableStateFlow(VpnTelemetry())
  val telemetry: StateFlow<VpnTelemetry> = _telemetry.asStateFlow()

  private val logIdCounter = AtomicLong(1L)

  fun setStatus(
    status: VpnConnectionStatus,
    serverId: String? = null,
    protocol: TunnelProtocol? = null,
    errorMessage: String? = null,
  ) {
    _telemetry.update { current ->
      val now = System.currentTimeMillis()
      val connectedAt =
        when (status) {
          VpnConnectionStatus.CONNECTED -> current.connectedSinceMs ?: now
          VpnConnectionStatus.DISCONNECTED,
          VpnConnectionStatus.ERROR -> null
          else -> current.connectedSinceMs
        }
      current.copy(
        status = status,
        activeServerId = serverId ?: current.activeServerId,
        activeProtocol = protocol ?: current.activeProtocol,
        connectedSinceMs = connectedAt,
        errorMessage = errorMessage,
        queriesResolved =
          if (status == VpnConnectionStatus.CONNECTING) 0L else current.queriesResolved,
        bytesIn = if (status == VpnConnectionStatus.CONNECTING) 0L else current.bytesIn,
        bytesOut = if (status == VpnConnectionStatus.CONNECTING) 0L else current.bytesOut,
      )
    }
  }

  fun recordDnsResolution(
    domain: String,
    recordType: String,
    latencyMs: Long,
    bytesOutDelta: Int,
    bytesInDelta: Int,
    serverIp: String,
    protocolLabel: String,
  ) {
    val entry =
      DnsLogEntry(
        id = logIdCounter.getAndIncrement(),
        timestampMs = System.currentTimeMillis(),
        domain = domain,
        recordType = recordType,
        latencyMs = latencyMs,
        serverIp = serverIp,
        protocolLabel = protocolLabel,
      )
    _telemetry.update { current ->
      current.copy(
        queriesResolved = current.queriesResolved + 1L,
        bytesOut = current.bytesOut + bytesOutDelta.toLong(),
        bytesIn = current.bytesIn + bytesInDelta.toLong(),
        lastLatencyMs = latencyMs,
        recentLogs = (listOf(entry) + current.recentLogs).take(40),
      )
    }
  }

  fun clearLogs() {
    _telemetry.update { it.copy(recentLogs = emptyList()) }
  }

  fun selectServerWhenDisconnected(serverId: String) {
    _telemetry.update { current ->
      if (current.status == VpnConnectionStatus.DISCONNECTED || current.status == VpnConnectionStatus.ERROR) {
        current.copy(activeServerId = GoogleUsaServers.findById(serverId).id)
      } else {
        current
      }
    }
  }
}
