package com.tanvrit.accounting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.tanvrit.accounting.navigation.AppNavigation
import com.tanvrit.accounting.theme.AccountingTheme

@Composable
fun App() {
    val navController = rememberNavController()

    AccountingTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NativeDesignSystem.colors.background),
        ) {
            AppNavigation(navController = navController)
        }
    }
}