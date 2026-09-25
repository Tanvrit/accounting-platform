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
fun ReportsScreen(navController: NavController) {
    Scaffold(
        title = "Financial Reports",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("Financial Reports")
            Text("Trial Balance", Modifier.padding(top = 16.dp))
            Text("Profit & Loss", Modifier.padding(top = 8.dp))
            Text("Balance Sheet", Modifier.padding(top = 8.dp))
            Text("Cash Flow", Modifier.padding(top = 8.dp))
            Text("Ratio Analysis", Modifier.padding(top = 8.dp))
            Text("Budget vs Actual", Modifier.padding(top = 8.dp))
            Text("Export to PDF/Excel/CSV", Modifier.padding(top = 8.dp))
        }
    }
}
