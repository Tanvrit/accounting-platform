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
fun BudgetScreen(navController: NavController) {
    Scaffold(
        title = "Budgeting",
        navigation = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        },
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).fillMaxSize().padding(16.dp)) {
            Text("Budgeting")
            Text("Grid: Accounts (rows) × Months (columns)", Modifier.padding(top = 16.dp))
            Text("Budget entry cell", Modifier.padding(top = 8.dp))
            Text("Variance report with waterfall chart", Modifier.padding(top = 8.dp))
            Text("Rolling forecast", Modifier.padding(top = 8.dp))
        }
    }
}
