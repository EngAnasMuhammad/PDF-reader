package com.example.pdfreader.presentation.navigation

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pdfreader.presentation.ui.screens.pdfScreen.PdfScreen
import com.example.pdfreader.presentation.state.PdfViewModel
import com.example.pdfreader.presentation.ui.screens.homeScreen.HomeScreen
import com.example.pdfreader.presentation.ui.screens.settingsScreen.SettingsScreen

@Composable
fun AppNavigation(
    startDestination: String = "selector",
    externalUri: Uri? = null
) {
    val navController = rememberNavController()
    val sharedViewModel: PdfViewModel = hiltViewModel()
    val activity = LocalActivity.current
    LaunchedEffect(externalUri) {
        externalUri?.let { uri ->
            sharedViewModel.loadPdf(uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

        NavHost(
            navController = navController,
            startDestination = startDestination,

            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                ) + fadeIn(tween(400))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(400)
                ) + fadeOut(tween(400))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                ) + fadeIn(tween(400))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(400)
                ) + fadeOut(tween(400))
            },
        ){
            composable(route = "selector"){
                HomeScreen (
                    viewModel = sharedViewModel,
                    onNavigateToPdf = {
                        navController.navigate("pdf")
                    },
                    onClickSetting = {
                        navController.navigate("settings")
                    }
                )
            }

            composable(route = "pdf"){
                PdfScreen(
                    viewModel = sharedViewModel,
                    onClickBack = {
                        if (navController.previousBackStackEntry != null){
                            navController.popBackStack()
                        }else{
                            activity?.finish()
                        }
                    },
                    onClickSetting = {
                        navController.navigate("settings")
                    }
                )
            }

            composable(route = "settings") {
                SettingsScreen(
                    viewModel = sharedViewModel,
                    onClickBack = { navController.popBackStack() }
                )
            }
        }
    }
}