package com.example.data

import com.example.model.DiagnosticVerificationResult
import com.example.model.VpnServer
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class NetworkDiagnosticsClient {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(4, TimeUnit.SECONDS)
      .readTimeout(4, TimeUnit.SECONDS)
      .build()

  suspend fun measureTcpLatencyMs(ipAddress: String, port: Int = 443): Long? =
    withContext(Dispatchers.IO) {
      runCatching {
          val startNs = System.nanoTime()
          Socket().use { socket ->
            socket.connect(InetSocketAddress(ipAddress, port), 2500)
          }
          val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
          elapsedMs.coerceAtLeast(1L)
        }
        .getOrNull()
    }

  suspend fun measureServerPing(server: VpnServer): Long? = measureTcpLatencyMs(server.primaryIp, 443)

  suspend fun verifyGoogleDnsAndPublicIp(): DiagnosticVerificationResult =
    withContext(Dispatchers.IO) {
      val dnsLatency = measureGoogleDohResolveLatency()
      val publicIp = fetchPublicIp()

      val isReachable = dnsLatency != null
      val summary =
        when {
          isReachable && publicIp != null ->
            "Google Public DNS (dns.google) responded in ${dnsLatency}ms. Public IP detected: $publicIp"
          isReachable ->
            "Google Public DNS (dns.google) responded in ${dnsLatency}ms over encrypted HTTPS."
          else ->
            "Unable to reach Google Public DNS. Check active Wi-Fi or cellular data connectivity."
        }

      DiagnosticVerificationResult(
        isVerifying = false,
        publicIp = publicIp,
        googleDnsReachable = isReachable,
        googleDnsLatencyMs = dnsLatency,
        verifiedAtMs = System.currentTimeMillis(),
        message = summary,
      )
    }

  private fun measureGoogleDohResolveLatency(): Long? =
    runCatching {
        val request =
          Request.Builder()
            .url("https://dns.google/resolve?name=google.com&type=A")
            .header("Accept", "application/dns-json")
            .build()
        val startNs = System.nanoTime()
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
          } else {
            null
          }
        }
      }
      .getOrNull()

  private fun fetchPublicIp(): String? =
    runCatching {
        val request = Request.Builder().url("https://api.ipify.org?format=json").build()
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@use null
          val body = response.body?.string() ?: return@use null
          JSONObject(body).optString("ip").takeIf { it.isNotBlank() }
        }
      }
      .getOrNull()
}
