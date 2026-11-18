package com.example

import com.example.data.VpnStateHolder
import com.example.model.GoogleUsaServers
import com.example.model.VpnConnectionStatus
import com.example.vpn.DnsPacketCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketCodecTest {

  private fun sampleDnsQueryForGoogleCom(): ByteArray {
    return byteArrayOf(
      0x12, 0x34, // Transaction ID
      0x01, 0x00, // Standard query
      0x00, 0x01, // QDCOUNT = 1
      0x00, 0x00, // ANCOUNT = 0
      0x00, 0x00, // NSCOUNT = 0
      0x00, 0x00, // ARCOUNT = 0
      // google.com
      0x06, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
      0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
      0x00,
      0x00, 0x01, // QTYPE = A (1)
      0x00, 0x01, // QCLASS = IN (1)
    )
  }

  private fun wrapIpv4Udp(
    srcIp: ByteArray,
    dstIp: ByteArray,
    srcPort: Int,
    dstPort: Int,
    payload: ByteArray,
  ): ByteArray {
    val udpLen = 8 + payload.size
    val totalLen = 20 + udpLen
    val pkt = ByteArray(totalLen)
    pkt[0] = 0x45.toByte()
    pkt[2] = ((totalLen ushr 8) and 0xFF).toByte()
    pkt[3] = (totalLen and 0xFF).toByte()
    pkt[8] = 64
    pkt[9] = 17 // UDP
    System.arraycopy(srcIp, 0, pkt, 12, 4)
    System.arraycopy(dstIp, 0, pkt, 16, 4)

    pkt[20] = ((srcPort ushr 8) and 0xFF).toByte()
    pkt[21] = (srcPort and 0xFF).toByte()
    pkt[22] = ((dstPort ushr 8) and 0xFF).toByte()
    pkt[23] = (dstPort and 0xFF).toByte()
    pkt[24] = ((udpLen ushr 8) and 0xFF).toByte()
    pkt[25] = (udpLen and 0xFF).toByte()
    System.arraycopy(payload, 0, pkt, 28, payload.size)
    return pkt
  }

  @Test
  fun `parseIpv4UdpPacket extracts addresses ports and DNS payload`() {
    val srcIp = byteArrayOf(10, 10, 8, 1)
    val dstIp = byteArrayOf(10, 10, 8, 2)
    val query = sampleDnsQueryForGoogleCom()
    val rawPacket = wrapIpv4Udp(srcIp, dstIp, 54321, 53, query)

    val parsed = DnsPacketCodec.parseIpv4UdpPacket(rawPacket, rawPacket.size)
    assertNotNull(parsed)
    assertArrayEquals(srcIp, parsed!!.sourceIp)
    assertArrayEquals(dstIp, parsed.destIp)
    assertEquals(54321, parsed.sourcePort)
    assertEquals(53, parsed.destPort)
    assertArrayEquals(query, parsed.payload)
  }

  @Test
  fun `extractDnsQuestionSummary decodes domain and A record type`() {
    val summary = DnsPacketCodec.extractDnsQuestionSummary(sampleDnsQueryForGoogleCom())
    assertEquals("google.com", summary.domain)
    assertEquals("A", summary.recordType)
  }

  @Test
  fun `buildIpv4UdpResponse swaps source and destination and parses cleanly`() {
    val srcIp = byteArrayOf(10, 10, 8, 1)
    val dstIp = byteArrayOf(10, 10, 8, 2)
    val query = sampleDnsQueryForGoogleCom()
    val rawRequest = wrapIpv4Udp(srcIp, dstIp, 51000, 53, query)

    val parsedReq = DnsPacketCodec.parseIpv4UdpPacket(rawRequest, rawRequest.size)!!
    val responseBytes = DnsPacketCodec.buildIpv4UdpResponse(parsedReq, query)
    val parsedResp = DnsPacketCodec.parseIpv4UdpPacket(responseBytes, responseBytes.size)

    assertNotNull(parsedResp)
    assertArrayEquals(dstIp, parsedResp!!.sourceIp)
    assertArrayEquals(srcIp, parsedResp.destIp)
    assertEquals(53, parsedResp.sourcePort)
    assertEquals(51000, parsedResp.destPort)
    assertArrayEquals(query, parsedResp.payload)
  }

  @Test
  fun `parseIpv4UdpPacket rejects truncated packet`() {
    assertNull(DnsPacketCodec.parseIpv4UdpPacket(byteArrayOf(0x45, 0x00), 2))
  }

  @Test
  fun `google USA servers contain 8_8_8_8 and 8_8_4_4`() {
    val ips = GoogleUsaServers.all.map { it.primaryIp }.toSet()
    assertEquals(setOf("8.8.8.8", "8.8.4.4"), ips)
  }

  @Test
  fun `vpn state holder records dns resolutions and resets on connect`() {
    VpnStateHolder.setStatus(VpnConnectionStatus.CONNECTING)
    VpnStateHolder.setStatus(VpnConnectionStatus.CONNECTED)
    VpnStateHolder.recordDnsResolution("example.com", "A", 14L, 32, 48, "8.8.8.8", "DoH")
    assertEquals(1L, VpnStateHolder.telemetry.value.queriesResolved)
    assertEquals("example.com", VpnStateHolder.telemetry.value.recentLogs.first().domain)
  }
}
