package com.omnitracker.renp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omnitracker.renp.navigation.AppNavHost
import com.omnitracker.renp.ui.theme.RenpTheme
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RenpTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: MainViewModel = koinViewModel()
                    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                    val start = startDestination
                    if (start != null) {
                        AppNavHost(startDestination = start)
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
