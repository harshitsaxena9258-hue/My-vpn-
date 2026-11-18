package com.example.vpn

import java.net.InetAddress

data class ParsedUdpPacket(
  val sourceIp: ByteArray,
  val destIp: ByteArray,
  val sourcePort: Int,
  val destPort: Int,
  val payload: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ParsedUdpPacket) return false
    return sourceIp.contentEquals(other.sourceIp) &&
      destIp.contentEquals(other.destIp) &&
      sourcePort == other.sourcePort &&
      destPort == other.destPort &&
      payload.contentEquals(other.payload)
  }

  override fun hashCode(): Int {
    var result = sourceIp.contentHashCode()
    result = 31 * result + destIp.contentHashCode()
    result = 31 * result + sourcePort
    result = 31 * result + destPort
    result = 31 * result + payload.contentHashCode()
    return result
  }
}

data class DnsQuestionSummary(val domain: String, val recordType: String)

object DnsPacketCodec {

  private const val IPV4_VERSION = 4
  private const val PROTOCOL_UDP = 17
  private const val MIN_IPV4_HEADER_LEN = 20
  private const val UDP_HEADER_LEN = 8

  fun parseIpv4UdpPacket(packet: ByteArray, length: Int): ParsedUdpPacket? {
    if (length < MIN_IPV4_HEADER_LEN + UDP_HEADER_LEN) return null

    val versionAndIhl = packet[0].toInt() and 0xFF
    val version = versionAndIhl ushr 4
    if (version != IPV4_VERSION) return null

    val ihlBytes = (versionAndIhl and 0x0F) * 4
    if (ihlBytes < MIN_IPV4_HEADER_LEN || length < ihlBytes + UDP_HEADER_LEN) return null

    val totalIpLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
    val effectiveLength = minOf(length, totalIpLength)
    if (effectiveLength < ihlBytes + UDP_HEADER_LEN) return null

    val protocol = packet[9].toInt() and 0xFF
    if (protocol != PROTOCOL_UDP) return null

    val sourceIp = packet.copyOfRange(12, 16)
    val destIp = packet.copyOfRange(16, 20)

    val udpOffset = ihlBytes
    val sourcePort =
      ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
    val destPort =
      ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
    val udpLength =
      ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or (packet[udpOffset + 5].toInt() and 0xFF)

    if (udpLength < UDP_HEADER_LEN) return null
    val payloadStart = udpOffset + UDP_HEADER_LEN
    val payloadEnd = minOf(effectiveLength, udpOffset + udpLength)
    if (payloadEnd <= payloadStart) return null

    val payload = packet.copyOfRange(payloadStart, payloadEnd)
    return ParsedUdpPacket(
      sourceIp = sourceIp,
      destIp = destIp,
      sourcePort = sourcePort,
      destPort = destPort,
      payload = payload,
    )
  }

  fun extractDnsQuestionSummary(dnsPayload: ByteArray): DnsQuestionSummary {
    if (dnsPayload.size < 13) return DnsQuestionSummary("unknown", "DNS")
    val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
    if (qdCount <= 0) return DnsQuestionSummary("unknown", "DNS")

    var offset = 12
    val labels = mutableListOf<String>()
    var steps = 0
    while (offset < dnsPayload.size && steps < 64) {
      steps++
      val labelLen = dnsPayload[offset].toInt() and 0xFF
      if (labelLen == 0) {
        offset++
        break
      }
      if ((labelLen and 0xC0) == 0xC0) {
        offset += 2
        break
      }
      offset++
      if (offset + labelLen > dnsPayload.size) break
      labels.add(String(dnsPayload, offset, labelLen, Charsets.US_ASCII))
      offset += labelLen
    }

    val recordType =
      if (offset + 2 <= dnsPayload.size) {
        val qType =
          ((dnsPayload[offset].toInt() and 0xFF) shl 8) or (dnsPayload[offset + 1].toInt() and 0xFF)
        when (qType) {
          1 -> "A"
          28 -> "AAAA"
          5 -> "CNAME"
          12 -> "PTR"
          15 -> "MX"
          16 -> "TXT"
          33 -> "SRV"
          65 -> "HTTPS"
          else -> "T$qType"
        }
      } else {
        "DNS"
      }

    val domain = if (labels.isNotEmpty()) labels.joinToString(".") else "unknown"
    return DnsQuestionSummary(domain = domain, recordType = recordType)
  }

  fun buildIpv4UdpResponse(request: ParsedUdpPacket, dnsResponsePayload: ByteArray): ByteArray {
    val udpLength = UDP_HEADER_LEN + dnsResponsePayload.size
    val totalLength = MIN_IPV4_HEADER_LEN + udpLength
    val packet = ByteArray(totalLength)

    // IPv4 Header (20 bytes)
    packet[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
    packet[1] = 0x00 // DSCP/ECN
    packet[2] = ((totalLength ushr 8) and 0xFF).toByte()
    packet[3] = (totalLength and 0xFF).toByte()
    packet[4] = 0x00 // Identification
    packet[5] = 0x01
    packet[6] = 0x40 // Don't Fragment
    packet[7] = 0x00
    packet[8] = 64 // TTL
    packet[9] = PROTOCOL_UDP.toByte()
    // Checksum bytes 10..11 left 0 before calculation
    System.arraycopy(request.destIp, 0, packet, 12, 4) // Src IP = original Dst IP
    System.arraycopy(request.sourceIp, 0, packet, 16, 4) // Dst IP = original Src IP

    val ipChecksum = computeChecksum(packet, 0, MIN_IPV4_HEADER_LEN)
    packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
    packet[11] = (ipChecksum and 0xFF).toByte()

    // UDP Header (8 bytes)
    val udpOffset = MIN_IPV4_HEADER_LEN
    packet[udpOffset] = ((request.destPort ushr 8) and 0xFF).toByte()
    packet[udpOffset + 1] = (request.destPort and 0xFF).toByte()
    packet[udpOffset + 2] = ((request.sourcePort ushr 8) and 0xFF).toByte()
    packet[udpOffset + 3] = (request.sourcePort and 0xFF).toByte()
    packet[udpOffset + 4] = ((udpLength ushr 8) and 0xFF).toByte()
    packet[udpOffset + 5] = (udpLength and 0xFF).toByte()
    // UDP Checksum bytes +6..+7 left 0 before calculation

    // Payload
    System.arraycopy(dnsResponsePayload, 0, packet, udpOffset + UDP_HEADER_LEN, dnsResponsePayload.size)

    val udpChecksum = computeUdpChecksum(packet, udpOffset, udpLength)
    packet[udpOffset + 6] = ((udpChecksum ushr 8) and 0xFF).toByte()
    packet[udpOffset + 7] = (udpChecksum and 0xFF).toByte()

    return packet
  }

  private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
    var sum = 0L
    var i = offset
    val end = offset + length
    while (i + 1 < end) {
      val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
      sum += word
      i += 2
    }
    if (i < end) {
      sum += (data[i].toInt() and 0xFF) shl 8
    }
    while ((sum ushr 16) != 0L) {
      sum = (sum and 0xFFFFL) + (sum ushr 16)
    }
    return (sum.inv() and 0xFFFFL).toInt()
  }

  private fun computeUdpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
    var sum = 0L
    // Pseudo-header: src IP (12..15), dst IP (16..19), zero + protocol (17), udpLength
    for (i in 12 until 20 step 2) {
      sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
    }
    sum += PROTOCOL_UDP.toLong()
    sum += udpLength.toLong()

    var i = udpOffset
    val end = udpOffset + udpLength
    while (i + 1 < end) {
      val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
      sum += word
      i += 2
    }
    if (i < end) {
      sum += (packet[i].toInt() and 0xFF) shl 8
    }
    while ((sum ushr 16) != 0L) {
      sum = (sum and 0xFFFFL) + (sum ushr 16)
    }
    val checksum = (sum.inv() and 0xFFFFL).toInt()
    return if (checksum == 0) 0xFFFF else checksum
  }

  fun formatIpv4(bytes: ByteArray): String =
    runCatching { InetAddress.getByAddress(bytes).hostAddress ?: "0.0.0.0" }.getOrDefault("0.0.0.0")
}
