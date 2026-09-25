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
fun AuditTrailScreen(navController: NavController) {
    Scaffold(
        title = "Audit Trail",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("Audit Trail")
            Text("Filterable log (date, user, entity, action)", Modifier.padding(top = 16.dp))
            Text("Before/After JSON diff view", Modifier.padding(top = 8.dp))
            Text("Hash chain verification: 'Verify Integrity' button", Modifier.padding(top = 8.dp))
            Text("Export immutable audit log", Modifier.padding(top = 8.dp))
        }
    }
}
