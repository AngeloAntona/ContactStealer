package com.example.contactreader.com.example.contactreader

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract

object ContactReader {

    /**
     * Legge tutti i contatti della rubrica in formato "Nome:Numero".
     */
    @SuppressLint("Range")
    fun readAllContacts(context: Context): List<String> {
        val contacts = mutableListOf<String>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Unknown"
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: "NoNumber"
                contacts.add("$name:$number")
            }
        }
        return contacts
    }
}