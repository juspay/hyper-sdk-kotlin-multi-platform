package `in`.juspay.hypersdk.kmp.sample

import kotlin.io.encoding.Base64
import kotlin.random.Random

object SampleConfig {
    // From sample/sandbox-credentials.properties; Juspay's demo clientId geddit by default
    const val CLIENT_ID = SandboxCredentials.CLIENT_ID
    const val MERCHANT_ID = SandboxCredentials.MERCHANT_ID
    const val CUSTOMER_ID = "kmp-sample-customer"
    const val CUSTOMER_MOBILE = "9999999999"
    const val CUSTOMER_EMAIL = "customer@example.com"
    const val FIRST_NAME = "KMP"
    const val LAST_NAME = "Sample"
    const val AMOUNT = "1.00"
}

/**
 * Payloads in the shapes the HyperSDK docs describe, for the signature-based flow: the app signs
 * the customer details (`signaturePayload`, initiate) and the order (`orderDetails`, payment page)
 * with the merchant's key (SHA256withRSA) and sends each with `signature` and `merchantKeyId`.
 *
 * DEMO ONLY. A real app must never ship the merchant private key. Create the order and the
 * signatures (or a session) on your backend and pass the result to the SDK.
 */
object SamplePayloads {
    /** Without sandbox credentials the payload goes out unsigned: the SDK still boots, but a payment cannot complete. */
    fun initiate(): String {
        val signed = if (hasCredentials) {
            val signaturePayload = """{"merchant_id":"${SampleConfig.MERCHANT_ID}","customer_id":"${SampleConfig.CUSTOMER_ID}","mobile_number":"${SampleConfig.CUSTOMER_MOBILE}","email_address":"${SampleConfig.CUSTOMER_EMAIL}","first_name":"${SampleConfig.FIRST_NAME}","last_name":"${SampleConfig.LAST_NAME}","timestamp":"${epochMillis()}"}"""
            signedMembers("signaturePayload", signaturePayload)
        } else {
            ""
        }
        return """
            {
              "requestId": "${requestId()}",
              "service": "in.juspay.hyperpay",
              "payload": {
                "action": "initiate",
                "merchantId": "${SampleConfig.MERCHANT_ID}",
                "clientId": "${SampleConfig.CLIENT_ID}",
                "environment": "sandbox",
                "customerId": "${SampleConfig.CUSTOMER_ID}"$signed
              }
            }
        """.trimIndent()
    }

    fun signedPaymentPage(): String {
        check(hasCredentials) {
            "Add merchantKeyId and privateKey to sample/sandbox-credentials.properties (see the .example file)"
        }
        val orderId = "kmp${epochMillis()}"
        val orderDetails = """{"order_id":"$orderId","first_name":"${SampleConfig.FIRST_NAME}","last_name":"${SampleConfig.LAST_NAME}","mobile_number":"${SampleConfig.CUSTOMER_MOBILE}","email_address":"${SampleConfig.CUSTOMER_EMAIL}","merchant_id":"${SampleConfig.MERCHANT_ID}","amount":"${SampleConfig.AMOUNT}","customer_id":"${SampleConfig.CUSTOMER_ID}","timestamp":"${epochMillis()}","return_url":"https://sandbox.juspay.in/end","currency":"INR"}"""
        return """
            {
              "requestId": "${requestId()}",
              "service": "in.juspay.hyperpay",
              "payload": {
                "action": "paymentPage",
                "merchantId": "${SampleConfig.MERCHANT_ID}",
                "clientId": "${SampleConfig.CLIENT_ID}",
                "environment": "sandbox",
                "language": "english",
                "customerId": "${SampleConfig.CUSTOMER_ID}",
                "customerMobile": "${SampleConfig.CUSTOMER_MOBILE}",
                "customerEmail": "${SampleConfig.CUSTOMER_EMAIL}",
                "orderId": "$orderId",
                "amount": "${SampleConfig.AMOUNT}"${signedMembers("orderDetails", orderDetails)}
              }
            }
        """.trimIndent()
    }

    private val hasCredentials: Boolean
        get() = SandboxCredentials.PRIVATE_KEY.isNotEmpty() && SandboxCredentials.MERCHANT_KEY_ID.isNotEmpty()

    /** The three members that carry a signed JSON string, prefixed with a comma so they can end an object. */
    private fun signedMembers(name: String, json: String): String {
        val signature = Base64.encode(
            signSha256WithRsa(json.encodeToByteArray(), RsaKeyDer.decode(SandboxCredentials.PRIVATE_KEY)),
        )
        return ",\n    \"merchantKeyId\": \"${SandboxCredentials.MERCHANT_KEY_ID}\",\n    \"signature\": \"$signature\",\n    \"$name\": ${jsonString(json)}"
    }

    private fun requestId(): String = "kmp-" + Random.nextLong().toString(16)

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
