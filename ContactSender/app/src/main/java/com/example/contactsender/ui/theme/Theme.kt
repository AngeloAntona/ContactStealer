package com.example.contactsender.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ContactSenderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        // colorScheme, typography, ecc. se vuoi personalizzare
        content = content
    )
}