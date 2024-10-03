package com.stasrg.incinerator.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stasrg.incinerator.screen.AwalScreen
import com.stasrg.incinerator.screen.WebScreen


@Composable
fun SetupNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Awal.route
    ) {
        composable(route = Screen.Awal.route) {
            AwalScreen(navController)
        }
        composable(route = Screen.Web.route) {
            WebScreen(navController)
        }
    }
}