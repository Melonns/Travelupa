// File: Screen.kt
package com.example.travelupa

sealed class Screen(val route: String) {
    object Greeting : Screen("greeting")      // TAMBAHKAN BARIS INI
    object Login : Screen("login")
    object RekomendasiTempat : Screen("rekomendasi_tempat")
}