// File: Screen.kt
package com.example.travelupa

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object RekomendasiTempat : Screen("rekomendasi_tempat")
}