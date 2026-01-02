package com.mari.aksa.page.buttombar

// Import komponen UI Compose
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mari.aksa.R

// DATA CLASS UNTUK 1 ITEM NAVBAR
// BottomNavItem hanya menyimpan:
// - route tujuan navigation
// - icon yang ditampilkan
// - title teksnya
data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val title: String
)

// FUNGSI UTAMA: BOTTOM NAVIGATION BAR

@Composable
fun BottomNavigationBar(navController: NavController) {

    // List item bottom nav yang akan ditampilkan
    // Bisa ditambah lagi kalau mau 3 tab, 4 tab, dst.
    val items = listOf(
        BottomNavItem(
            "home",                      // route halaman Home
            Icons.Filled.Home,           // icon bawaan Android
            stringResource(R.string.nav_home) // teks "Home"
        ),

        BottomNavItem(
            "dictionary",                // route halaman Dictionary
            ImageVector.vectorResource(id = R.drawable.ic_dictionary), // icon custom svg
            stringResource(R.string.nav_dictionary) // teks "Dictionary"
        )
    )

    // Ambil route halaman yang SEDANG AKTIF (halaman sekarang)
    // Dipakai untuk menentukan icon dan text yang sedang diseleksi
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    // COMPONENT NAVIGATION BAR (UI)
    NavigationBar(
        containerColor = Color.White,   // warna background navbar
        modifier = Modifier.height(60.dp) // tinggi navbar
    ) {

        // Loop setiap item (Home & Dictionary)
        items.forEach { item ->

            val selected = currentRoute == item.route // cek apakah tab ini aktif
            val contentColor =
                if (selected) Color(0xFFFF9800) // warna oranye saat aktif
                else Color.Gray                // warna abu saat tidak aktif

            // 1 ITEM NAVIGATON BAR
            NavigationBarItem(
                selected = selected,  // status apakah tab aktif

                // Ketika item diklik
                onClick = {
                    if (!selected) {  // kalau tab belum aktif → navigate
                        navController.navigate(item.route) {

                            // Hindari membuat halaman baru yang sama
                            launchSingleTop = true

                            // Mengembalikan state terakhir halaman
                            restoreState = true

                            // Pop sampai ke halaman awal, tapi simpan state
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                        }
                    }
                },

                // ISI ICON & TEXT BOTTOM NAV (manual)
                // Karena:
                // alwaysShowLabel = false, maka kita bikin label sendiri
                icon = {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        // ICON
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = contentColor // warna berubah sesuai selected
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // TEKS LABEL
                        Text(
                            text = item.title,
                            color = contentColor,
                            fontSize = 12.sp
                        )
                    }
                },

                alwaysShowLabel = false, // label bawaan Material3 dimatikan

                // Hilangkan bulatan/indicator ungu default saat tab aktif
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
