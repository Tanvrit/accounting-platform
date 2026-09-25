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
fun TdsCenterScreen(navController: NavController) {
    Scaffold(
        title = "TDS Center",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("TDS Center - Deductee, Challan, Returns, Certificates")
            Text("Deductee-wise TDS deducted", Modifier.padding(top = 16.dp))
            Text("Challan 281 entry", Modifier.padding(top = 8.dp))
            Text("Auto-link to deductees", Modifier.padding(top = 8.dp))
            Text("Generate 26Q/27Q/24Q", Modifier.padding(top = 8.dp))
            Text("Generate Form 16/16A", Modifier.padding(top = 8.dp))
        }
    }
}
