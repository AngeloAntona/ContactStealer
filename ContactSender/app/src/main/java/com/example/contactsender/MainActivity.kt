package com.example.contactsender

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.contactsender.ui.theme.ContactSenderTheme

class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    // BFSKReceiver con ridondanza 3x
    private val bfskReceiver by lazy {
        BFSKReceiver(
            context = this,
            onBitReceived = { bit ->
                bitIndicator = bit.toString()
            },
            onContactReceived = { contact ->
                info = "Contatto ricevuto:\n$contact"
                contactsCount++
            }
        )
    }

    private var info by mutableStateOf("Nessun contatto ricevuto")
    private var bitIndicator by mutableStateOf("_")
    private var contactsCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                info = "Permesso microfono negato!"
            }
        }

        setContent {
            ContactSenderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                    MainScreen(
                        modifier = Modifier.padding(pad),
                        info = info,
                        bitIndicator = bitIndicator,
                        contactsCount = contactsCount,
                        onStartListening = { checkAndStart() },
                        onStopListening = { stopListening() }
                    )
                }
            }
        }
    }

    private fun checkAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        info = "In ascolto BFSK..."
        bitIndicator = "_"
        contactsCount = 0
        bfskReceiver.startListening()
    }

    private fun stopListening() {
        bfskReceiver.stopListening()
        info = "Ascolto interrotto, contatti ricevuti: $contactsCount"
    }

    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        info: String,
        bitIndicator: String,
        contactsCount: Int,
        onStartListening: () -> Unit,
        onStopListening: () -> Unit
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onStartListening) {
                    Text("Start Listening")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStopListening) {
                    Text("Stop Listening")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Bit corrente: $bitIndicator")
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = info)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Contatti ricevuti: $contactsCount")
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        ContactSenderTheme {
            MainScreen(
                info = "Anteprima",
                bitIndicator = "0",
                contactsCount = 1,
                onStartListening = {},
                onStopListening = {}
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bfskReceiver.stopListening()
    }
}