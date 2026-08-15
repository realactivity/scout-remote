package dev.lantern.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.lantern.app.brand.BrandTokens
import dev.lantern.app.brand.LanternTheme
import dev.lantern.core.application.InstantClock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val tokens = BrandTokens.fromJson(assets.open("brand-tokens.json").bufferedReader().readText())
        val app = application as LanternApp
        setContent {
            val viewModel = remember {
                HomeViewModel(app.commandService, app.syncService, InstantClock.System)
            }
            LanternTheme(tokens) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(viewModel, InstantClock.System)
                }
            }
        }
    }
}
