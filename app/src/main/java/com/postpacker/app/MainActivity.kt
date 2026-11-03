package com.postpacker.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.postpacker.app.ui.screens.HistoryScreen
import com.postpacker.app.ui.screens.HomeScreen
import com.postpacker.app.ui.theme.PostPackerTheme
import com.postpacker.app.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved packaged archives
        viewModel.loadHistory(this)

        // Handle initial intent if shared from Instagram / Facebook
        handleIntent(intent)

        setContent {
            PostPackerTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }

        // Observe toast events
        lifecycleScopeLaunch {
            viewModel.toastEvent.collectLatest { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (Intent.ACTION_SEND == intent.action && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleIncomingShare(sharedText)
            }
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        androidx.lifecycle.lifecycleScope.launchWhenStarted {
            block()
        }
    }
}

@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("貼文打包") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Archive, contentDescription = "History") },
                    label = { Text("歷史檔案") }
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            HistoryScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
