package shelli.com.myhyperioncontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import shelli.com.myhyperioncontrol.ui.theme.MyHyperionControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyHyperionControlTheme {
                val vm: HyperionViewModel = viewModel()
                var showSettings by remember { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(
                        viewModel      = vm,
                        onNavigateBack = { showSettings = false }
                    )
                } else {
                    MainScreen(
                        viewModel             = vm,
                        onNavigateToSettings  = { showSettings = true }
                    )
                }
            }
        }
    }
}