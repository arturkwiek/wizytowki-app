package com.wizytowki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wizytowki.ui.home.HomeViewModel
import com.wizytowki.ui.scan.ScanPrototypeScreen
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

/** Route names for the 9 screens; real screens replace the placeholders in later stages. */
object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val SCAN_FRONT = "scan_front"
    const val SCAN_BACK = "scan_back"
    const val PREVIEW = "preview"
    const val DUPLICATE = "duplicate"
    const val CONTACT_DETAIL = "contact_detail/{id}"
    const val COMPANY_DETAIL = "company_detail/{id}"
    const val SETTINGS = "settings"

    fun contactDetail(id: Long) = "contact_detail/$id"
    fun companyDetail(id: Long) = "company_detail/$id"
}

private data class NavLink(@StringRes val label: Int, val route: String)

@Composable
fun WizytowkiNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val contactCount by viewModel.contactCount.collectAsState()
            PlaceholderScreen(
                navController, R.string.nav_home, showBack = false,
                subtitle = stringResource(R.string.home_contacts_count, contactCount),
                links = listOf(
                    NavLink(R.string.nav_scan, Routes.SCAN),
                    NavLink(R.string.screen_contact_detail, Routes.contactDetail(1)),
                    NavLink(R.string.screen_company_detail, Routes.companyDetail(1)),
                    NavLink(R.string.nav_settings, Routes.SETTINGS),
                )
            )
        }
        composable(Routes.SCAN) {
            // Etap 1 (#7): scanner prototype; SCAN_FRONT/SCAN_BACK get the chosen implementation in #8.
            ScanPrototypeScreen(onBack = { navController.popBackStack() }, viewModel = hiltViewModel())
        }
        composable(Routes.SCAN_FRONT) {
            PlaceholderScreen(navController, R.string.screen_scan_front, links = listOf(NavLink(R.string.screen_scan_back, Routes.SCAN_BACK)))
        }
        composable(Routes.SCAN_BACK) {
            PlaceholderScreen(
                navController, R.string.screen_scan_back,
                links = listOf(
                    NavLink(R.string.preview_title, Routes.PREVIEW),
                    NavLink(R.string.scan_skip_back, Routes.PREVIEW),
                )
            )
        }
        composable(Routes.PREVIEW) {
            PlaceholderScreen(navController, R.string.preview_title, links = listOf(NavLink(R.string.screen_duplicate, Routes.DUPLICATE)))
        }
        composable(Routes.DUPLICATE) {
            PlaceholderScreen(
                navController, R.string.screen_duplicate,
                links = listOf(NavLink(R.string.screen_contact_detail, Routes.contactDetail(1)))
            )
        }
        composable(Routes.CONTACT_DETAIL) {
            PlaceholderScreen(
                navController, R.string.screen_contact_detail,
                links = listOf(NavLink(R.string.screen_company_detail, Routes.companyDetail(1)))
            )
        }
        composable(Routes.COMPANY_DETAIL) { PlaceholderScreen(navController, R.string.screen_company_detail) }
        composable(Routes.SETTINGS) { PlaceholderScreen(navController, R.string.nav_settings) }
    }
}

@Composable
private fun PlaceholderScreen(
    navController: NavController,
    @StringRes title: Int,
    links: List<NavLink> = emptyList(),
    showBack: Boolean = true,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(subtitle ?: stringResource(R.string.placeholder_note), style = MaterialTheme.typography.bodyMedium)
        links.forEach { link ->
            Button(onClick = { navController.navigate(link.route) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(link.label))
            }
        }
        if (showBack) {
            OutlinedButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}
