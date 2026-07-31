package com.paperflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.paperflow.app.navigation.PaperFlowNavGraph
import com.paperflow.app.ui.theme.PaperFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PaperFlowApp()
        }
    }
}

@Composable
private fun PaperFlowApp() {
    PaperFlowTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            PaperFlowNavGraph(navController = navController)
        }
    }
}

