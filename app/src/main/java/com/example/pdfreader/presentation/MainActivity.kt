package com.example.pdfreader.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdfreader.domain.model.AppLanguage
import com.example.pdfreader.domain.model.AppTheme
import com.example.pdfreader.presentation.navigation.AppNavigation
import com.example.pdfreader.presentation.state.PdfViewModel
import com.example.pdfreader.presentation.ui.theme.PDFReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // 1. Make the external intent reactive so Compose listens for updates
    private val _externalPdfUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle the intent if the app is launched from a cold start
        handleIntent(intent)

        setContent {
            val viewModel: PdfViewModel = hiltViewModel()
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

            // 2. Observe the intent URI inside Compose
            val externalUri by _externalPdfUri.collectAsStateWithLifecycle()

            // 3. Dynamically set the destination based on the reactive URI
            val startDestination = if (externalUri != null) "pdf" else "selector"

            LaunchedEffect(appLanguage) {
                val localeList = if (appLanguage == AppLanguage.SYSTEM) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(appLanguage.tag)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            val darkTheme = when (appTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            PDFReaderTheme(darkTheme = darkTheme) {
                AppNavigation(
                    startDestination = startDestination,
                    externalUri = externalUri
                )
            }
        }
    }

    // 4. Override onNewIntent to catch files opened while the app is already in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the Activity's intent so it has the latest data
        setIntent(intent)
        handleIntent(intent)
    }

    // 5. Extract the parsing logic to keep the lifecycle methods clean
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            _externalPdfUri.value = intent.data
        }
    }
}