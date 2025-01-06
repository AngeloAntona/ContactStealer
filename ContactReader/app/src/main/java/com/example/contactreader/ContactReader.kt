package com.example.contactreader.com.example.contactreader

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract

object ContactReader {

    /**
     * Legge un solo contatto e lo restituisce in formato "Nome:Numero".
     */
    @SuppressLint("Range")
    fun readSingleContact(context: Context): String {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            null,
            null,
            null
        )
        cursor?.use {
            if (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "Unknown"
                val number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: "NoNumber"
                return "$name:$number"
            }
        }
        return "NoContact:0"
    }
}