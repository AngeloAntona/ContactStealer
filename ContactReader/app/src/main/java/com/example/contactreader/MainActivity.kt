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

class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var info by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                sendContact()
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
                        onClickSend = { checkPermissionAndSend() }
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
            sendContact()
        }
    }

    private fun sendContact() {
        info = "Leggo contatto..."
        val contact = ContactReader.readSingleContact(this)
        info = "Trasmetto: $contact"
        BFSKTransmitter.transmitSingleContact(contact)
    }

    @Composable
    fun MainScreen(
        modifier: Modifier = Modifier,
        info: String,
        onClickSend: () -> Unit
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onClickSend) {
                    Text(text = "Invia UN Contatto")
                }
                Spacer(modifier = Modifier.height(20.dp))
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
                onClickSend = {}
            )
        }
    }
}