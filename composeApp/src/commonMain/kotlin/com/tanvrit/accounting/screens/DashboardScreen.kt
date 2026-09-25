package com.tanvrit.accounting.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun DashboardScreen(navController: NavController) {
    Scaffold(
        title = "Dashboard",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("Dashboard - Real-time KPIs")
            Text("Cash position", Modifier.weight(1f).padding(top = 16.dp))
            Text("AR/AP aging", Modifier.weight(1f).padding(top = 8.dp))
            Text("Top expenses", Modifier.weight(1f).padding(top = 8.dp))
            Text("GST liability", Modifier.weight(1f).padding(top = 8.dp))
            Text("TDS liability", Modifier.weight(1f).padding(top = 8.dp))
        }
    }
}