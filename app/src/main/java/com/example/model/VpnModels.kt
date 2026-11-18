package com.example.model

enum class VpnConnectionStatus {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  DISCONNECTING,
  ERROR,
}

enum class TunnelProtocol(val displayName: String, val shortName: String, val subtitle: String) {
  DOH_HTTPS(
    displayName = "Google DoH (TLS 1.3)",
    shortName = "DoH HTTPS",
    subtitle = "Encrypts DNS inside HTTPS to dns.google (Port 443)",
  ),
  PROTECTED_UDP(
    displayName = "Google Public DNS (UDP)",
    shortName = "UDP 53",
    subtitle = "Ultra-low overhead protected socket to 8.8.8.8:53",
  ),
}

data class VpnServer(
  val id: String,
  val name: String,
  val region: String,
  val primaryIp: String,
  val secondaryIp: String,
  val ipv6Primary: String,
  val dohEndpoint: String,
  val badge: String,
  val pingMs: Long? = null,
  val isCheckingPing: Boolean = false,
)

object GoogleUsaServers {
  val all: List<VpnServer> =
    listOf(
      VpnServer(
        id = "us_google_primary",
        name = "USA — Google Primary",
        region = "Mountain View, CA • Anycast",
        primaryIp = "8.8.8.8",
        secondaryIp = "8.8.4.4",
        ipv6Primary = "2001:4860:4860::8888",
        dohEndpoint = "https://dns.google/dns-query",
        badge = "Fastest",
      ),
      VpnServer(
        id = "us_google_east",
        name = "USA — Google East",
        region = "Ashburn, VA • Anycast",
        primaryIp = "8.8.4.4",
        secondaryIp = "8.8.8.8",
        ipv6Primary = "2001:4860:4860::8844",
        dohEndpoint = "https://dns.google/dns-query",
        badge = "Low Latency",
      ),
      VpnServer(
        id = "us_google_central",
        name = "USA — Google Central",
        region = "Dallas, TX • Dual-Stack",
        primaryIp = "8.8.8.8",
        secondaryIp = "8.8.4.4",
        ipv6Primary = "2001:4860:4860::8888",
        dohEndpoint = "https://dns.google/dns-query",
        badge = "IPv6 Ready",
      ),
      VpnServer(
        id = "us_google_midwest",
        name = "USA — Google Shield",
        region = "Council Bluffs, IA • DNSSEC",
        primaryIp = "8.8.4.4",
        secondaryIp = "8.8.8.8",
        ipv6Primary = "2001:4860:4860::8844",
        dohEndpoint = "https://dns.google/dns-query",
        badge = "DNSSEC",
      ),
    )

  fun findById(id: String): VpnServer = all.firstOrNull { it.id == id } ?: all.first()
}

data class DnsLogEntry(
  val id: Long,
  val timestampMs: Long,
  val domain: String,
  val recordType: String,
  val latencyMs: Long,
  val serverIp: String,
  val protocolLabel: String,
)

data class VpnTelemetry(
  val status: VpnConnectionStatus = VpnConnectionStatus.DISCONNECTED,
  val activeServerId: String = GoogleUsaServers.all.first().id,
  val activeProtocol: TunnelProtocol = TunnelProtocol.DOH_HTTPS,
  val connectedSinceMs: Long? = null,
  val queriesResolved: Long = 0L,
  val bytesIn: Long = 0L,
  val bytesOut: Long = 0L,
  val lastLatencyMs: Long? = null,
  val errorMessage: String? = null,
  val recentLogs: List<DnsLogEntry> = emptyList(),
)

data class DiagnosticVerificationResult(
  val isVerifying: Boolean = false,
  val publicIp: String? = null,
  val googleDnsReachable: Boolean? = null,
  val googleDnsLatencyMs: Long? = null,
  val verifiedAtMs: Long? = null,
  val message: String? = null,
)
