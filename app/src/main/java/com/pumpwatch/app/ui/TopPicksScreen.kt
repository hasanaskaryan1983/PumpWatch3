package com.pumpwatch.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.engine.BatchScanner
import com.pumpwatch.app.engine.SignalResult
import com.pumpwatch.app.store.PicksStore
import kotlinx.coroutines.launch
import java.util.Locale

private val Green = Color(0xFF00E676)
private val Red = Color(0xFFFF5252)
private val Gold = Color(0xFFFFC107)
private val Blue = Color(0xFF4FC3F7)

// ---------- صفحه برترین‌های روز ----------

@Composable
fun TopPicksScreen(mode: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var picks by remember { mutableStateOf<List<SignalResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var progressText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var lastScan by remember { mutableStateOf(0L) }

    fun reload() {
        picks = PicksStore.loadToday(context, mode)?.picks ?: emptyList()
        lastScan = PicksStore.lastScan(context, mode)
    }

    LaunchedEffect(mode) { reload() }

   
