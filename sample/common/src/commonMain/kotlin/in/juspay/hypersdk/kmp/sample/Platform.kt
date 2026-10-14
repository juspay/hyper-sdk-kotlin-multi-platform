package `in`.juspay.hypersdk.kmp.sample

/** SHA256withRSA (PKCS#1 v1.5) over [data] with a DER-encoded RSA private key; returns the raw signature. */
internal expect fun signSha256WithRsa(data: ByteArray, privateKeyDer: ByteArray): ByteArray

internal expect fun epochMillis(): Long
