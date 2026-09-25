package com.tanvrit.accounting.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun VoucherEntryScreen(navController: NavController) {
    Scaffold(
        title = "Voucher Entry",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("Voucher Entry - Single-screen, keyboard-first")
            Text("Smart defaults: Sale/Purchase/Receipt/Payment/Journal/Contra", Modifier.weight(1f).padding(top = 16.dp))
            Text("Real-time validation: Double-entry balance (green/red indicator)", Modifier.weight(1f).padding(top = 8.dp))
        }
    }
}
