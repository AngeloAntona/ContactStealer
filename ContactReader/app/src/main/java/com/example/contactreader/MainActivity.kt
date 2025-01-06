package com.example.contactreader

import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.contactreader.com.example.contactreader.BFSKTransmitter
import com.example.contactreader.com.example.contactreader.ContactReader
import com.example.contactreader.ui.theme.ContactReaderTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var info by mutableStateOf("")
    private var isTransmitting by mutableStateOf(false)
    private var transmittingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inizializza il permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startSendingContacts()
            } else {
                info = "Permesso contatti negato."
            }
        }

        setContent {
            ContactReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                    MainScreen(
                        modifier = Modifier.padding(pad),
                        info = info,
                        isTransmitting = isTransmitting,
                        onClickStart = { checkPermissionAndSend() },
                        onClickStop = { stopSendingContacts() }
                    )
                }
            }
        }
    }

    private fun checkPermissionAndSend() {
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        } else {
            startSendingContacts()
        }
    }

    private fun startSendingContacts() {
        info = "Inizio trasmissione contatti..."
        isTransmitting = true
        val contacts = ContactReader.readAllContacts(this)

        // Avviamo un job in background per trasmettere i contatti uno alla volta
        transmittingJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                for (contact in contacts) {
                    if (!isTransmitting) break
                    withContext(Dispatchers.Main) {
                        info = "Trasmetto: $contact"
                    }

                    // Attende la trasmissione del contatto
                    BFSKTransmitter.transmitSingleContact(contact)

                    // Pausa tra un contatto e l'altro
                    delay(1000)
                }
                withContext(Dispatchers.Main) {
                    info = "Tutti i contatti trasmessi."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    info = "Errore durante la trasmissione: ${e.message}"
                }
            }
        }
    }

    private fun stopSendingContacts() {
        isTransmitting = false
        transmittingJob?.cancel()
        info = "Trasmissione interrotta."
    }

    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        info: String,
        isTransmitting: Boolean,
        onClickStart: () -> Unit,
        onClickStop: () -> Unit
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onClickStart, enabled = !isTransmitting) {
                    Text("Avvia Trasmissione")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClickStop, enabled = isTransmitting) {
                    Text("Ferma Trasmissione")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = info)
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        ContactReaderTheme {
            MainScreen(
                info = "Anteprima",
                isTransmitting = false,
                onClickStart = {},
                onClickStop = {}
            )
        }
    }
}