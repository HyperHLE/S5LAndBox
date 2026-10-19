package com.tayga16.s5landbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tayga16.s5landbox.ui.EmulatorScreen
import com.tayga16.s5landbox.ui.ImportScreen
import com.tayga16.s5landbox.ui.MachinesScreen
import com.tayga16.s5landbox.ui.SettingsScreen
import com.tayga16.s5landbox.ui.SnapshotsScreen
import com.tayga16.s5landbox.ui.rememberRepository
import com.tayga16.s5landbox.ui.theme.S5LAndBoxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S5LAndBoxTheme {
                S5LAndBoxNav()
            }
        }
    }
}

@Composable
private fun S5LAndBoxNav() {
    val repository = rememberRepository()
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "machines") {
        composable("machines") {
            MachinesScreen(
                repository = repository,
                onOpen = { id -> nav.navigate("machine/$id") },
                onImport = { nav.navigate("import") },
                onSettings = { id -> nav.navigate("settings/$id") },
                onSnapshots = { id -> nav.navigate("snapshots/$id") },
            )
        }
        composable("import") {
            ImportScreen(repository = repository, onDone = { nav.popBackStack() })
        }
        composable(
            route = "machine/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            EmulatorScreen(
                repository = repository,
                machineId = id,
                onBack = { nav.popBackStack() },
                onSettings = { nav.navigate("settings/$id") },
                onSnapshots = { nav.navigate("snapshots/$id") },
            )
        }
        composable(
            route = "settings/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SettingsScreen(
                repository = repository,
                machineId = id,
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = "snapshots/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            SnapshotsScreen(
                repository = repository,
                machineId = id,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
