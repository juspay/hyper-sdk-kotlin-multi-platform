package `in`.juspay.hypersdk.kmp.sample

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256

@OptIn(ExperimentalForeignApi::class)
internal actual fun signSha256WithRsa(data: ByteArray, privateKeyDer: ByteArray): ByteArray = memScoped {
    val pkcs1 = RsaKeyDer.toPkcs1(privateKeyDer)
    val bits = alloc<IntVar>().apply { value = RsaKeyDer.modulusBits(pkcs1) }
    val keySize = CFNumberCreate(null, kCFNumberIntType, bits.ptr)
    val keys = allocArrayOf(kSecAttrKeyType, kSecAttrKeyClass, kSecAttrKeySizeInBits)
    val values = allocArrayOf(kSecAttrKeyTypeRSA, kSecAttrKeyClassPrivate, keySize)
    val attributes = CFDictionaryCreate(
        null, keys.reinterpret(), values.reinterpret(), 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
    )
    val error = alloc<CFErrorRefVar>()
    val keyData = pkcs1.toCfData()
    val message = data.toCfData()
    var key: SecKeyRef? = null
    try {
        key = SecKeyCreateWithData(keyData, attributes, error.ptr)
            ?: error("SecKeyCreateWithData failed: ${error.describe()}")
        val signature = SecKeyCreateSignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256, message, error.ptr)
            ?: error("SecKeyCreateSignature failed: ${error.describe()}")
        // CFBridgingRelease hands the +1 reference to ARC, so the signature needs no CFRelease
        (CFBridgingRelease(signature) as NSData).toByteArray()
    } finally {
        key?.let { CFRelease(it) }
        error.value?.let { CFRelease(it) }
        CFRelease(message)
        CFRelease(keyData)
        CFRelease(attributes)
        CFRelease(keySize)
    }
}

internal actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toCfData(): CFDataRef {
    val nsData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
    val retained: CPointer<*> = CFBridgingRetain(nsData) ?: error("CFBridgingRetain returned null")
    return retained.reinterpret()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun CFErrorRefVar.describe(): String =
    value?.let { CFBridgingRelease(CFErrorCopyDescription(it))?.toString() } ?: "unknown error"
