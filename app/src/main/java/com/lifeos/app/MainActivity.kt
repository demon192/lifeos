package com.lifeos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.lifeos.app.ui.theme.LifeOSTheme

/**
 * The single screen entry point for LifeOS.
 *
 * Milestone 1 does exactly one thing: open the camera, point it at text, and show the
 * text that ML Kit reads — live, on-device. No AI, no saving yet. That comes in M2/M3.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeOSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // CameraScreen holds all the M1 logic (permission + camera + OCR).
                    CameraScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
