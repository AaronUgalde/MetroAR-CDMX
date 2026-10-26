package mx.metroar.cdmx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import mx.metroar.cdmx.presentation.MainViewModel
import mx.metroar.cdmx.ui.MetroArRoot
import mx.metroar.cdmx.ui.theme.MetroArTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        (application as MetroArApp).container.viewModelFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetroArTheme {
                MetroArRoot(viewModel)
            }
        }
    }
}
