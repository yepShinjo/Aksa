package com.mari.aksa.page.buttombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mari.aksa.page.HomePage
import com.mari.aksa.page.ResultPage
import com.mari.aksa.page.dictionary.DictionaryPage
import com.mari.aksa.page.dictionary.DictionaryViewModel
import com.mari.aksa.page.dictionary.DictionaryViewModelFactory
import com.mari.aksa.page.setting.AboutPage
import com.mari.aksa.page.setting.SettingPage
import com.mari.aksa.tutorial.isFirstLaunchGuideNeeded
import com.mari.aksa.tutorial.markAllGuidesDoneOnce
import com.mari.aksa.tutorial.startFullGuide

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val dictionaryViewModel: DictionaryViewModel =
        viewModel(factory = DictionaryViewModelFactory())

    LaunchedEffect(Unit) {
        if (isFirstLaunchGuideNeeded(context)) {
            startFullGuide(context)
            markAllGuidesDoneOnce(context)
        }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) },
        // ini yang bikin HomePage (kamera) bisa "naik" sampai atas layar
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->

        // ambil padding bawah saja (biar tidak ketutup bottom bar)
        val bottomPad = innerPadding.calculateBottomPadding()

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPad)
        ) {
            composable("home") { HomePage(navController) }
            composable("dictionary") {
                DictionaryPage(viewModel = dictionaryViewModel, navController = navController)
            }
            composable("result") { ResultPage(navController = navController) }
            composable("setting") { SettingPage(navController = navController) }
            composable("about") { AboutPage(navController = navController) }
        }
    }
}
