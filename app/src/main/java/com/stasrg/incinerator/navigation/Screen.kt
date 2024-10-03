package com.stasrg.incinerator.navigation

sealed class Screen(val route: String) {
    data object Awal : Screen("awalScreen")

    data object Web : Screen("webScreen")

}