package com.example.contactsender

import android.content.Context
import android.util.Log
// Import necessari
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import java.io.InputStream

object SheetsHelper {
    // ID del tuo foglio Google Sheets (non l'email dell'account).
    // Copia l'ID da: docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit
    private const val SPREADSHEET_ID = "1cFt7YYEqyqlkFpxa2whT5szX52RvbLDy83oonyB7vog"

    // Nome foglio e range di colonne
    private const val RANGE = "Foglio1!A:C"

    private var sheetsService: Sheets? = null

    fun init(context: Context) {
        // Se già inizializzato, non facciamo nulla
        if (sheetsService != null) return

        try {
            // Carichiamo il JSON delle credenziali da assets/credentials.json
            val inputStream: InputStream = context.assets.open("credentials.json")

            val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
            val credential = GoogleCredential.fromStream(
                inputStream,
                AndroidHttp.newCompatibleTransport(),
                jsonFactory
            ).createScoped(listOf("https://www.googleapis.com/auth/spreadsheets"))

            // Costruiamo il client Google Sheets
            sheetsService = Sheets.Builder(
                AndroidHttp.newCompatibleTransport(),
                jsonFactory,
                credential
            )
                .setApplicationName("ContactSenderApp")
                .build()

            Log.d("SheetsHelper", "Inizializzazione Google Sheets completata.")
        } catch (e: Exception) {
            Log.e("SheetsHelper", "Errore init Sheets: ${e.message}", e)
        }
    }

    /**
     * Aggiunge (append) una riga al foglio Foglio1, colonne A-C
     */
    fun appendRowToSheet(contact: String) {
        if (sheetsService == null) {
            Log.e("SheetsHelper", "Sheets Service non inizializzato")
            return
        }

        val parts = contact.split(":")
        val name = parts.getOrNull(0) ?: "Unknown"
        val number = parts.getOrNull(1) ?: "NoNumber"
        val timestamp = System.currentTimeMillis().toString()

        // Creiamo la riga da inviare
        val rowData = listOf(listOf<Any>(name, number, timestamp))
        val body = ValueRange().setValues(rowData)

        try {
            sheetsService?.spreadsheets()?.values()
                ?.append(SPREADSHEET_ID, RANGE, body)
                ?.setValueInputOption("RAW")
                ?.execute()

            Log.d("SheetsHelper", "Riga aggiunta a Google Sheets: $contact")
        } catch (e: Exception) {
            Log.e("SheetsHelper", "Errore nell'appendRowToSheet: ${e.message}", e)
        }
    }
}