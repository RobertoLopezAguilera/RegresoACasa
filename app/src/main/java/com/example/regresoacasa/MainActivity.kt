package com.example.regresoacasa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.regresoacasa.ui.theme.RegresoACasaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RegresoACasaTheme {
                MapScreen()
            }
        }
    }
}
