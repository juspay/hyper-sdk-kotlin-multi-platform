package `in`.juspay.hypersdk.kmp.sample.nativeapp

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import `in`.juspay.hypersdk.kmp.HyperSdk
import `in`.juspay.hypersdk.kmp.sample.Checkout

/**
 * Native Android UI (plain Views) over the shared [Checkout] logic. The Activity creates the
 * session, because HyperSDK needs a FragmentActivity host, and hands it to shared code.
 */
class MainActivity : FragmentActivity() {
    private lateinit var checkout: Checkout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val log = TextView(this)
        // Events arrive on the main thread, so the view can be updated directly
        checkout = Checkout(HyperSdk(this)) { line -> log.text = "$line\n\n${log.text}" }

        val initiate = Button(this).apply { text = "Initiate" }
        val pay = Button(this).apply {
            text = "Pay ₹1.00"
            isEnabled = false
        }
        val terminate = Button(this).apply {
            text = "Terminate"
            isEnabled = false
        }
        initiate.setOnClickListener {
            checkout.initiate()
            initiate.isEnabled = false
            pay.isEnabled = true
            terminate.isEnabled = true
        }
        pay.setOnClickListener { checkout.pay() }
        terminate.setOnClickListener {
            checkout.terminate()
            initiate.isEnabled = true
            pay.isEnabled = false
            terminate.isEnabled = false
        }

        val padding = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(padding, padding, padding, padding)
            addView(
                TextView(this@MainActivity).apply {
                    text = "HyperSDK KMP native sample"
                    textSize = 20f
                },
            )
            addView(
                LinearLayout(this@MainActivity).apply {
                    addView(initiate)
                    addView(pay)
                    addView(terminate)
                },
            )
            addView(ScrollView(this@MainActivity).apply { addView(log) })
        }
        setContentView(content)
    }

    override fun onDestroy() {
        checkout.terminate()
        super.onDestroy()
    }
}
