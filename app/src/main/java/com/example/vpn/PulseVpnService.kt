package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.VpnStateHolder
import com.example.model.GoogleUsaServers
import com.example.model.TunnelProtocol
import com.example.model.VpnConnectionStatus
import com.example.model.VpnServer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PulseVpnService : VpnService() {

  companion object {
    const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
    const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"
    const val EXTRA_SERVER_ID = "extra_server_id"
    const val EXTRA_PROTOCOL = "extra_protocol"
    const val EXTRA_IPV6 = "extra_ipv6"

    private const val NOTIFICATION_CHANNEL_ID = "pulse_vpn_channel"
    private const val NOTIFICATION_ID = 1001
    private const val TUN_LOCAL_IPV4 = "10.10.8.1"
    private const val TUN_DNS_IPV4 = "10.10.8.2"
    private const val TUN_LOCAL_IPV6 = "fd00:10:8::1"
    private const val DNS_PORT = 53
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val writeLock = Any()
  private val inFlightSemaphore = Semaphore(32)

  private var tunnelInterface: ParcelFileDescriptor? = null
  private var packetLoopJob: Job? = null
  private var activeServer: VpnServer = GoogleUsaServers.all.first()
  private var activeProtocol: TunnelProtocol = TunnelProtocol.DOH_HTTPS
  private var dohClient: OkHttpClient? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_DISCONNECT -> {
        disconnectTunnel()
        return START_NOT_STICKY
      }
      ACTION_CONNECT,
      null -> {
        val serverId = intent?.getStringExtra(EXTRA_SERVER_ID) ?: GoogleUsaServers.all.first().id
        val protocolName = intent?.getStringExtra(EXTRA_PROTOCOL) ?: TunnelProtocol.DOH_HTTPS.name
        val ipv6Enabled = intent?.getBooleanExtra(EXTRA_IPV6, true) ?: true

        val server = GoogleUsaServers.findById(serverId)
        val protocol =
          TunnelProtocol.entries.firstOrNull { it.name == protocolName }
            ?: TunnelProtocol.DOH_HTTPS

        startForegroundNotification(server)
        connectTunnel(server, protocol, ipv6Enabled)
        return START_STICKY
      }
      else -> return START_NOT_STICKY
    }
  }

  private fun startForegroundNotification(server: VpnServer) {
    val notification = buildNotification(server)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun connectTunnel(server: VpnServer, protocol: TunnelProtocol, ipv6Enabled: Boolean) {
    packetLoopJob?.cancel()
    closeTunnelFd()

    activeServer = server
    activeProtocol = protocol
    VpnStateHolder.setStatus(
      status = VpnConnectionStatus.CONNECTING,
      serverId = server.id,
      protocol = protocol,
      errorMessage = null,
    )

    try {
      val builder =
        Builder()
          .setSession("PulseVPN • ${server.name}")
          .setMtu(1500)
          .addAddress(TUN_LOCAL_IPV4, 30)
          .addDnsServer(TUN_DNS_IPV4)
          .addRoute(TUN_DNS_IPV4, 32)

      if (ipv6Enabled) {
        builder.addAddress(TUN_LOCAL_IPV6, 128)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        builder.setMetered(false)
      }

      val openAppIntent =
        PendingIntent.getActivity(
          this,
          0,
          Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      builder.setConfigureIntent(openAppIntent)

      val pfd = builder.establish()
      if (pfd == null) {
        VpnStateHolder.setStatus(
          status = VpnConnectionStatus.ERROR,
          serverId = server.id,
          protocol = protocol,
          errorMessage = getString(R.string.vpn_permission_denied),
        )
        stopSelf()
        return
      }

      tunnelInterface = pfd
      dohClient = buildProtectedDohClient(server)

      VpnStateHolder.setStatus(
        status = VpnConnectionStatus.CONNECTED,
        serverId = server.id,
        protocol = protocol,
        errorMessage = null,
      )

      val notificationManager = getSystemService(NotificationManager::class.java)
      notificationManager.notify(NOTIFICATION_ID, buildNotification(server))

      packetLoopJob = serviceScope.launch { runPacketLoop(pfd, server, protocol) }
    } catch (e: Exception) {
      VpnStateHolder.setStatus(
        status = VpnConnectionStatus.ERROR,
        serverId = server.id,
        protocol = protocol,
        errorMessage = e.localizedMessage ?: "Failed to establish VPN tunnel",
      )
      disconnectTunnel()
    }
  }

  private suspend fun runPacketLoop(
    pfd: ParcelFileDescriptor,
    server: VpnServer,
    protocol: TunnelProtocol,
  ) {
    val input = FileInputStream(pfd.fileDescriptor)
    val output = FileOutputStream(pfd.fileDescriptor)
    val buffer = ByteArray(32767)

    while (serviceScope.isActive) {
      val readBytes =
        try {
          input.read(buffer)
        } catch (_: IOException) {
          break
        }

      if (readBytes <= 0) continue

      val parsed = DnsPacketCodec.parseIpv4UdpPacket(buffer, readBytes) ?: continue
      if (parsed.destPort != DNS_PORT) continue

      serviceScope.launch {
        inFlightSemaphore.withPermit {
          handleDnsPacket(parsed, output, server, protocol)
        }
      }
    }
  }

  private fun handleDnsPacket(
    request: ParsedUdpPacket,
    output: FileOutputStream,
    server: VpnServer,
    protocol: TunnelProtocol,
  ) {
    val startNs = System.nanoTime()
    val question = DnsPacketCodec.extractDnsQuestionSummary(request.payload)

    val responsePayload: ByteArray?
    val usedLabel: String
    if (protocol == TunnelProtocol.DOH_HTTPS) {
      val dohResult = resolveViaGoogleDoh(request.payload, server)
      if (dohResult != null) {
        responsePayload = dohResult
        usedLabel = "DoH"
      } else {
        responsePayload = resolveViaProtectedUdp(request.payload, server)
        usedLabel = "UDP"
      }
    } else {
      responsePayload = resolveViaProtectedUdp(request.payload, server)
      usedLabel = "UDP"
    }

    if (responsePayload == null) return

    val responseIpPacket = DnsPacketCodec.buildIpv4UdpResponse(request, responsePayload)
    val written =
      synchronized(writeLock) {
        try {
          output.write(responseIpPacket)
          true
        } catch (_: IOException) {
          false
        }
      }

    if (written) {
      val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
      VpnStateHolder.recordDnsResolution(
        domain = question.domain,
        recordType = question.recordType,
        latencyMs = latencyMs,
        bytesOutDelta = request.payload.size,
        bytesInDelta = responsePayload.size,
        serverIp = server.primaryIp,
        protocolLabel = usedLabel,
      )
    }
  }

  private fun resolveViaGoogleDoh(dnsQuery: ByteArray, server: VpnServer): ByteArray? {
    val client = dohClient ?: return null
    return runCatching {
        val mediaType = "application/dns-message".toMediaType()
        val request =
          Request.Builder()
            .url(server.dohEndpoint)
            .post(dnsQuery.toRequestBody(mediaType))
            .header("Accept", "application/dns-message")
            .build()

        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@use null
          response.body?.bytes()?.takeIf { it.isNotEmpty() }
        }
      }
      .getOrNull()
  }

  private fun resolveViaProtectedUdp(dnsQuery: ByteArray, server: VpnServer): ByteArray? {
    return runCatching {
        DatagramSocket().use { socket ->
          protect(socket)
          socket.soTimeout = 3000
          val targetAddr = InetAddress.getByName(server.primaryIp)
          val outPacket = DatagramPacket(dnsQuery, dnsQuery.size, targetAddr, DNS_PORT)
          socket.send(outPacket)

          val responseBuf = ByteArray(4096)
          val inPacket = DatagramPacket(responseBuf, responseBuf.size)
          socket.receive(inPacket)
          responseBuf.copyOfRange(0, inPacket.length)
        }
      }
      .getOrNull()
  }

  private fun buildProtectedDohClient(server: VpnServer): OkHttpClient {
    val protectedSocketFactory =
      object : SocketFactory() {
        private val delegate = getDefault()

        override fun createSocket(): Socket =
          delegate.createSocket().also { protect(it) }

        override fun createSocket(host: String?, port: Int): Socket =
          delegate.createSocket(host, port).also { protect(it) }

        override fun createSocket(
          host: String?,
          port: Int,
          localHost: InetAddress?,
          localPort: Int,
        ): Socket = delegate.createSocket(host, port, localHost, localPort).also { protect(it) }

        override fun createSocket(host: InetAddress?, port: Int): Socket =
          delegate.createSocket(host, port).also { protect(it) }

        override fun createSocket(
          address: InetAddress?,
          port: Int,
          localAddress: InetAddress?,
          localPort: Int,
        ): Socket =
          delegate.createSocket(address, port, localAddress, localPort).also { protect(it) }
      }

    val staticGoogleDns =
      object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
          return if (hostname.equals("dns.google", ignoreCase = true)) {
            listOf(
              InetAddress.getByName(server.primaryIp),
              InetAddress.getByName(server.secondaryIp),
            )
          } else {
            Dns.SYSTEM.lookup(hostname)
          }
        }
      }

    return OkHttpClient.Builder()
      .socketFactory(protectedSocketFactory)
      .dns(staticGoogleDns)
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(4, TimeUnit.SECONDS)
      .build()
  }

  private fun disconnectTunnel() {
    packetLoopJob?.cancel()
    packetLoopJob = null
    closeTunnelFd()
    VpnStateHolder.setStatus(VpnConnectionStatus.DISCONNECTED)
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun closeTunnelFd() {
    try {
      tunnelInterface?.close()
    } catch (_: IOException) {}
    tunnelInterface = null
  }

  private fun buildNotification(server: VpnServer): Notification {
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val disconnectIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, PulseVpnService::class.java).apply { action = ACTION_DISCONNECT },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_lock_lock)
      .setContentTitle(getString(R.string.notification_title_connected))
      .setContentText(
        getString(R.string.notification_text_connected, server.name, server.primaryIp)
      )
      .setContentIntent(openIntent)
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        getString(R.string.notification_action_disconnect),
        disconnectIntent,
      )
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = getString(R.string.notification_channel_desc) }
      val manager = getSystemService(NotificationManager::class.java)
      manager.createNotificationChannel(channel)
    }
  }

  override fun onRevoke() {
    disconnectTunnel()
    super.onRevoke()
  }

  override fun onDestroy() {
    packetLoopJob?.cancel()
    closeTunnelFd()
    serviceScope.cancel()
    super.onDestroy()
  }
}
