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
fun GstCenterScreen(navController: NavController) {
    Scaffold(
        title = "GST Center",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("GST Center - GSTR-1, GSTR-3B, E-Invoice, E-Way Bill")
            Text("Auto-compute GSTR-1 (B2B/B2C/Export/CDNR/HSN)", Modifier.padding(top = 16.dp))
            Text("Auto-compute GSTR-3B from GSTR-1 + Purchase register", Modifier.padding(top = 8.dp))
            Text("Generate IRN + QR code via NIC/GSTN API", Modifier.padding(top = 8.dp))
            Text("Generate E-Way Bill with transport details", Modifier.padding(top = 8.dp))
        }
    }
}
