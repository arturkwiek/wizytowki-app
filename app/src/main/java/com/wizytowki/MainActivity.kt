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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wizytowki.ui.home.DraftCard
import com.wizytowki.ui.home.HomeViewModel
import com.wizytowki.ui.preview.PreviewScreen
import com.wizytowki.ui.scan.ScanScreen
import com.wizytowki.ui.scan.ScanViewModel
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
    const val SCAN = "scan?${ScanViewModel.ARG_RESUME}={${ScanViewModel.ARG_RESUME}}"
    const val PREVIEW = "preview"
    const val DUPLICATE = "duplicate"
    const val CONTACT_DETAIL = "contact_detail/{id}"
    const val COMPANY_DETAIL = "company_detail/{id}"
    const val SETTINGS = "settings"

    fun scan(resume: Boolean = false) = if (resume) "scan?${ScanViewModel.ARG_RESUME}=true" else "scan"
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
            val draft by viewModel.draft.collectAsState()
            PlaceholderScreen(
                navController, R.string.nav_home, showBack = false,
                subtitle = stringResource(R.string.home_contacts_count, contactCount),
                header = {
                    draft?.let {
                        DraftCard(
                            draft = it,
                            onResume = { navController.navigate(Routes.scan(resume = true)) },
                            onDiscard = viewModel::discardDraft,
                        )
                    }
                },
                links = listOf(
                    NavLink(R.string.nav_scan, Routes.scan()),
                    NavLink(R.string.screen_contact_detail, Routes.contactDetail(1)),
                    NavLink(R.string.screen_company_detail, Routes.companyDetail(1)),
                    NavLink(R.string.nav_settings, Routes.SETTINGS),
                )
            )
        }
        composable(
            Routes.SCAN,
            arguments = listOf(navArgument(ScanViewModel.ARG_RESUME) { type = NavType.BoolType; defaultValue = false }),
        ) {
            // Screens "Aparat – przód" and "Aparat – tył" (REQUIREMENTS 8a) share one ViewModel.
            ScanScreen(
                viewModel = hiltViewModel(),
                onCancel = { navController.popBackStack() },
                onDone = { navController.navigate(Routes.PREVIEW) },
            )
        }
        composable(Routes.PREVIEW) {
            PreviewScreen(
                viewModel = hiltViewModel(),
                // New scan from a clean stack: Back from it returns home, not to the previous card.
                onScanNext = {
                    navController.navigate(Routes.scan()) { popUpTo(Routes.HOME) }
                },
            )
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
    header: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        Text(subtitle ?: stringResource(R.string.placeholder_note), style = MaterialTheme.typography.bodyMedium)
        header()
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
