package com.wizytowki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wizytowki.ui.theme.WizytowkiAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WizytowkiAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WizytowkiNavigation()
                }
            }
        }
    }
}

@Composable
fun WizytowkiNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("scan") { ScanScreen(navController) }
        composable("scan_front") { ScanFrontScreen(navController) }
        composable("scan_back") { ScanBackScreen(navController) }
        composable("preview") { PreviewScreen(navController) }
        composable("duplicate") { DuplicateScreen(navController) }
        composable("contact_detail/{id}") { ContactDetailScreen(navController) }
        composable("company_detail/{id}") { CompanyDetailScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
    }
}

@Composable
fun HomeScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Home Screen - Placeholder")
}

@Composable
fun ScanScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Scan Screen - Placeholder")
}

@Composable
fun ScanFrontScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Scan Front Screen - Placeholder")
}

@Composable
fun ScanBackScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Scan Back Screen - Placeholder")
}

@Composable
fun PreviewScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Preview Screen - Placeholder")
}

@Composable
fun DuplicateScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Duplicate Screen - Placeholder")
}

@Composable
fun ContactDetailScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Contact Detail Screen - Placeholder")
}

@Composable
fun CompanyDetailScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Company Detail Screen - Placeholder")
}

@Composable
fun SettingsScreen(navController: androidx.navigation.NavController) {
    androidx.compose.material3.Text("Settings Screen - Placeholder")
}
