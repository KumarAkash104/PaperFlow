package com.paperflow.app

import android.content.Intent
import android.net.Uri
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
import com.paperflow.app.util.FileUtils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val externalPdfPath = extractPdfPathFromIntent(intent)

        setContent {
            PaperFlowApp(initialExternalPdfPath = externalPdfPath)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractPdfPathFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            val uri: Uri = intent.data ?: return null
            return FileUtils.resolveUriToFilePath(this, uri)
        }
        return null
    }
}

@Composable
private fun PaperFlowApp(initialExternalPdfPath: String? = null) {
    PaperFlowTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            PaperFlowNavGraph(
                navController = navController,
                initialExternalPdfPath = initialExternalPdfPath
            )
        }
    }
}
