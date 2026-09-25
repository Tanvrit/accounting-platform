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
fun FiscalPeriodsScreen(navController: NavController) {
    Scaffold(
        title = "Fiscal Periods",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("Fiscal Periods")
            Text("List with status badges (OPEN/LOCKED/CLOSED)", Modifier.padding(top = 16.dp))
            Text("Create periods (Year → Quarter → Month)", Modifier.padding(top = 8.dp))
            Text("Set opening balances (auto from prior close)", Modifier.padding(top = 8.dp))
            Text("Review & confirm", Modifier.padding(top = 8.dp))
            Text("Lock/Unlock period", Modifier.padding(top = 8.dp))
            Text("Carry forward opening balances", Modifier.padding(top = 8.dp))
        }
    }
}
