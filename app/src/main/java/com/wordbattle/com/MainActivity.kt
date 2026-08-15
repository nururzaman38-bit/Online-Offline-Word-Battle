package com.wordbattle.com

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordbattle.com.ui.MainViewModel
import com.wordbattle.com.ui.navigation.WordBattleNavGraph
import com.wordbattle.com.ui.theme.WordBattleTheme

/**
 * Uses [AppCompatActivity] so `AppCompatDelegate.setApplicationLocales` can switch the app language
 * instantly and persist it (see `AppLocalesMetadataHolderService` in the manifest).
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordBattleTheme {
                val viewModel: MainViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WordBattleNavGraph(state, viewModel, this@MainActivity)
            }
        }
    }
}
