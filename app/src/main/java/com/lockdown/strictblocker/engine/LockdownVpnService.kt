package com.lockdown.strictblocker.engine

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.lockdown.strictblocker.core.Prefs
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Website blocking without root, via a local VPN that only routes DNS.
 *
 * The tunnel advertises 8.8.8.8 / 1.1.1.1 as its DNS servers and routes just
 * those two addresses, so the only traffic entering the tunnel is DNS.
 * Each query's domain is checked against the block list:
 *  - blocked -> sinkholed locally (A -> 0.0.0.0, AAAA -> ::)
 *  - allowed -> forwarded to the real resolver over a protected socket and
 *    the answer is relayed back.
 * No other traffic is touched, and nothing is logged.
 *
 * Domain-level blocking is what DNS can honestly do: full URL paths inside
 * HTTPS are encrypted. Keyword / full-URL rules are enforced separately by
 * the Accessibility Service reading browser address bars.
 */
class LockdownVpnService : VpnService() {

    private var iface: ParcelFileDescriptor? = null

    @Volatile
    private var running = false
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "lockdown-dns").apply { isDaemon = true }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) startVpn()
        return START_STICKY
    }

    override fun onRevoke() {
        // User switched the VPN off in system settings.
        Prefs.get(this).vpnEnabled = false
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        val descriptor = Builder()
            .setSession("LOCKDOWN")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(UPSTREAM_DNS)
            .addRoute(UPSTREAM_DNS, 32)
            .addRoute(SECONDARY_DNS, 32)
            .setMtu(1500)
            .establish() ?: return
        iface = descriptor
        running = true
        Prefs.get(this).vpnRunning = true
        Thread({ tunnelLoop(descriptor) }, "lockdown-tun").apply { isDaemon = true }.start()
    }

    private fun stopVpn() {
        running = false
        Prefs.get(this).vpnRunning = false
        runCatching { iface?.close() }
        iface = null
    }

    // ---- tunnel ----

    private fun tunnelLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        while (running) {
            val len = try {
                input.read(buffer)
            } catch (e: IOException) {
                break
            }
            if (len <= 0) continue
            val packet = buffer.copyOf(len)
            executor.execute { handlePacket(packet, output) }
        }
    }

    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        if (packet.size < 28) return
        if ((packet[0].toInt() shr 4) != 4) return // IPv4 only
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (packet.size < ihl + 8) return
        if ((packet[9].toInt() and 0xFF) != 17) return // UDP only
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val srcPort = u16(packet, ihl)
        val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53) return
        val udpLen = u16(packet, ihl + 4)
        val payloadEnd = minOf(packet.size, ihl + udpLen).coerceAtLeast(ihl + 8)
        val dns = packet.copyOfRange(ihl + 8, payloadEnd)

        val question = parseQuestion(dns)
        if (question != null && BlockEngine.isDomainBlocked(this, question.domain)) {
            val response = buildSinkholeResponse(dns, question) ?: return
            writePacket(output, wrapUdp(response, dstIp, srcIp, 53, srcPort))
        } else {
            forward(dns, srcIp, srcPort, dstIp, output)
        }
    }

    private fun forward(
        dns: ByteArray,
        clientIp: ByteArray,
        clientPort: Int,
        serverIp: ByteArray,
        output: FileOutputStream
    ) {
        val socket = DatagramSocket()
        try {
            protect(socket)
            socket.soTimeout = 3_000
            val upstream = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(dns, dns.size, upstream, 53))
            val buf = ByteArray(32767)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            val responseDns = buf.copyOf(reply.length)
            writePacket(output, wrapUdp(responseDns, serverIp, clientIp, 53, clientPort))
        } catch (e: Exception) {
            // Drop the query; the resolver will retry.
        } finally {
            socket.close()
        }
    }

    private fun writePacket(output: FileOutputStream, packet: ByteArray) {
        synchronized(output) {
            runCatching {
                output.write(packet)
                output.flush()
            }
        }
    }

    // ---- DNS parsing / building ----

    private data class DnsQuestion(
        val domain: String,
        val qtype: Int,
        val qclass: Int,
        val questionEnd: Int
    )

    private fun parseQuestion(dns: ByteArray): DnsQuestion? {
        if (dns.size < 12) return null
        if (u16(dns, 4) < 1) return null
        var off = 12
        val labels = mutableListOf<String>()
        while (off < dns.size) {
            val l = dns[off].toInt() and 0xFF
            if (l == 0) {
                off++
                break
            }
            if (l and 0xC0 != 0) return null // compression is not used in questions
            off++
            if (off + l > dns.size) return null
            labels.add(String(dns, off, l, Charsets.UTF_8))
            off += l
        }
        if (off + 4 > dns.size) return null
        return DnsQuestion(
            domain = labels.joinToString("."),
            qtype = u16(dns, off),
            qclass = u16(dns, off + 2),
            questionEnd = off + 4
        )
    }

    private fun buildSinkholeResponse(dns: ByteArray, q: DnsQuestion): ByteArray? {
        val isA = q.qtype == 1
        val isAAAA = q.qtype == 28
        val answerCount = if (isA || isAAAA) 1 else 0
        val rdata = if (isA) byteArrayOf(0, 0, 0, 0) else ByteArray(16)
        val out = ByteArrayOutputStream()
        out.write(dns[0].toInt())
        out.write(dns[1].toInt())
        out.write(0x81) // QR=1, RD=1
        out.write(0x80) // RA=1, RCODE=0
        writeU16(out, u16(dns, 4))
        writeU16(out, answerCount)
        writeU16(out, 0)
        writeU16(out, 0)
        out.write(dns, 12, q.questionEnd - 12)
        if (answerCount == 1) {
            out.write(0xC0)
            out.write(0x0C) // pointer to the question name at offset 12
            writeU16(out, q.qtype)
            writeU16(out, q.qclass)
            writeU32(out, 60)
            writeU16(out, rdata.size)
            out.write(rdata)
        }
        return out.toByteArray()
    }

    // ---- IPv4 / UDP wrapping ----

    private fun wrapUdp(
        payload: ByteArray,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int
    ): ByteArray {
        val udpLen = 8 + payload.size
        val totalLen = 20 + udpLen
        val pkt = ByteArray(totalLen)
        pkt[0] = 0x45
        putU16(pkt, 2, totalLen)
        pkt[8] = 64 // TTL
        pkt[9] = 17 // UDP
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)
        putU16(pkt, 10, checksum(pkt, 0, 20))
        putU16(pkt, 20, srcPort)
        putU16(pkt, 22, dstPort)
        putU16(pkt, 24, udpLen)
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        // UDP checksum over pseudo-header + UDP segment
        val pseudo = ByteArray(12)
        System.arraycopy(srcIp, 0, pseudo, 0, 4)
        System.arraycopy(dstIp, 0, pseudo, 4, 4)
        pseudo[9] = 17
        putU16(pseudo, 10, udpLen)
        val combined = pseudo + pkt.copyOfRange(20, totalLen)
        var udpChecksum = checksum(combined, 0, combined.size)
        if (udpChecksum == 0) udpChecksum = 0xFFFF
        putU16(pkt, 26, udpChecksum)
        return pkt
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun u16(data: ByteArray, off: Int): Int =
        ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)

    private fun putU16(data: ByteArray, off: Int, value: Int) {
        data[off] = (value shr 8).toByte()
        data[off + 1] = value.toByte()
    }

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    companion object {
        const val ACTION_STOP = "com.lockdown.strictblocker.STOP_VPN"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val SECONDARY_DNS = "1.1.1.1"

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, LockdownVpnService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, LockdownVpnService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
