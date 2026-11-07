package com.omsati.dropwatch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var sharedUrl by mutableStateOf<String?>(null)
    private var openId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            DropWatchRoot(
                sharedUrl = sharedUrl, onSharedConsumed = { sharedUrl = null },
                openId = openId, onOpenConsumed = { openId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            sharedUrl = Regex("https?://\\S+").find(text)?.value ?: text.takeIf { it.isNotBlank() }
        }
        val id = i.getLongExtra("productId", -1L)
        if (id > 0) openId = id
    }
}
