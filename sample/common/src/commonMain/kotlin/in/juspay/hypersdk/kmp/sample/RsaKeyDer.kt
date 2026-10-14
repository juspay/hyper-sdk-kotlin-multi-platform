package `in`.juspay.hypersdk.kmp.sample

import kotlin.io.encoding.Base64

/**
 * Just enough DER handling to accept an RSA private key in either PKCS#8 or PKCS#1 form:
 * Android's KeyFactory wants PKCS#8, iOS's SecKeyCreateWithData wants PKCS#1.
 */
internal object RsaKeyDer {
    private const val SEQUENCE = 0x30
    private const val INTEGER = 0x02
    private const val OCTET_STRING = 0x04

    // AlgorithmIdentifier { rsaEncryption, NULL }
    private val RSA_ALGORITHM_ID = byteArrayOf(
        0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d,
        0x01, 0x01, 0x01, 0x05, 0x00,
    )

    /** Decodes base64 DER, with or without PEM header lines and line breaks. */
    fun decode(key: String): ByteArray {
        val body = key.lineSequence()
            .filterNot { it.trim().startsWith("-----") }
            .joinToString("")
            .filterNot { it.isWhitespace() }
        return Base64.decode(body)
    }

    fun toPkcs8(der: ByteArray): ByteArray =
        if (isPkcs8(der)) der else tlv(SEQUENCE, byteArrayOf(INTEGER.toByte(), 0x01, 0x00) + RSA_ALGORITHM_ID + tlv(OCTET_STRING, der))

    fun toPkcs1(der: ByteArray): ByteArray {
        if (!isPkcs8(der)) return der
        val outer = readTlv(der, 0)
        val version = readTlv(der, outer.contentStart)
        val algorithm = readTlv(der, version.contentStart + version.length)
        val key = readTlv(der, algorithm.contentStart + algorithm.length)
        require(key.tag == OCTET_STRING) { "Unexpected PKCS#8 layout" }
        return der.copyOfRange(key.contentStart, key.contentStart + key.length)
    }

    /** Key size in bits, read from the modulus of a PKCS#1 key. */
    fun modulusBits(pkcs1: ByteArray): Int {
        val outer = readTlv(pkcs1, 0)
        val version = readTlv(pkcs1, outer.contentStart)
        val modulus = readTlv(pkcs1, version.contentStart + version.length)
        require(modulus.tag == INTEGER) { "Unexpected PKCS#1 layout" }
        val leadingZero = if (pkcs1[modulus.contentStart].toInt() == 0) 1 else 0
        return (modulus.length - leadingZero) * 8
    }

    // PKCS#8 is SEQUENCE { INTEGER 0, SEQUENCE algorithm, OCTET STRING key };
    // PKCS#1 is SEQUENCE { INTEGER 0, INTEGER modulus, ... }
    private fun isPkcs8(der: ByteArray): Boolean {
        val outer = readTlv(der, 0)
        require(outer.tag == SEQUENCE) { "Not a DER-encoded RSA private key" }
        val version = readTlv(der, outer.contentStart)
        return readTlv(der, version.contentStart + version.length).tag == SEQUENCE
    }

    private class Tlv(val tag: Int, val length: Int, val contentStart: Int)

    private fun readTlv(der: ByteArray, offset: Int): Tlv {
        val tag = der[offset].toInt() and 0xff
        var pos = offset + 1
        var length = der[pos++].toInt() and 0xff
        if (length and 0x80 != 0) {
            val byteCount = length and 0x7f
            length = 0
            repeat(byteCount) { length = (length shl 8) or (der[pos++].toInt() and 0xff) }
        }
        return Tlv(tag, length, pos)
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val length = content.size
        val lengthBytes = when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length < 0x100 -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), length.toByte())
        }
        return byteArrayOf(tag.toByte()) + lengthBytes + content
    }
}
