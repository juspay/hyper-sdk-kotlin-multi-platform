package `in`.juspay.hypersdk.kmp.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.juspay.hypersdk.kmp.HyperSdk

@Composable
fun App() {
    MaterialTheme {
        var hyperSdk by remember { mutableStateOf<HyperSdk?>(null) }
        val events = remember { mutableStateListOf<String>() }

        // End the session when the screen goes away
        DisposableEffect(Unit) {
            onDispose { hyperSdk?.terminate() }
        }

        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("HyperSDK KMP sample", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = hyperSdk == null,
                    onClick = {
                        try {
                            // Created on demand, on the current screen
                            val session = HyperSdk()
                            session.initiate(SamplePayloads.initiate()) { event ->
                                events.add(0, "${event.name}  ${event.json}")
                            }
                            hyperSdk = session
                        } catch (e: Exception) {
                            events.add(0, "error  ${e.message}")
                        }
                    },
                ) { Text("Initiate") }

                Button(
                    enabled = hyperSdk != null,
                    onClick = {
                        try {
                            hyperSdk?.process(SamplePayloads.signedPaymentPage())
                        } catch (e: Exception) {
                            events.add(0, "error  ${e.message}")
                        }
                    },
                ) { Text("Pay ₹${SampleConfig.AMOUNT}") }

                Button(
                    enabled = hyperSdk != null,
                    onClick = {
                        hyperSdk?.terminate()
                        hyperSdk = null
                    },
                ) { Text("Terminate") }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
