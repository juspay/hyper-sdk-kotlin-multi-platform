package `in`.juspay.hypersdk.kmp.sample

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

internal actual fun signSha256WithRsa(data: ByteArray, privateKeyDer: ByteArray): ByteArray {
    val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(RsaKeyDer.toPkcs8(privateKeyDer)))
    return Signature.getInstance("SHA256withRSA").apply {
        initSign(key)
        update(data)
    }.sign()
}

internal actual fun epochMillis(): Long = System.currentTimeMillis()
