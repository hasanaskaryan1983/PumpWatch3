package com.pumpwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpwatch.app.store.DayPicks
import com.pumpwatch.app.store.PicksStore

private val HGreen = Color(0xFF00E676)
private val HRed = Color(0xFFFF5252)

// ---------- صفحه تاریخچه ----------

@Composable
fun HistoryScreen(mode: String) {
    val context = LocalContext.current

    var history by remember { mutableStateOf<List<DayPicks>>(emptyList()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    fun reload() {
        history = PicksStore.loadHistory(context, mode)
    }

    LaunchedEffect(mode) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- سربرگ ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (mode == "SPOT") "📚 تاریخچه اسپات" else "📚 تاریخچه فیوچرز",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("حذف همه 🗑️", color = HRed, fontSize = 12.sp)
                }
            }
        }

        // ---------- محتوا ----------
        if (history.isEmpty()) {
            Text(
                "هنوز تاریخچه‌ای ثبت نشده.\nبعد از اولین اسکن، لیست هر روز این‌جا ذخیره می‌شه.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { day ->
                    DayCard(
                        day = day,
                        expanded = expanded == day.date,
                        onToggle = { expanded = if (expanded == day.date) null else day.date },
                        onDelete = { deleteTarget = day.date }
                    )
                }
            }
        }
    }

    // ---------- دیالوگ حذف یک روز ----------
    deleteTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف تاریخچه") },
            text = { Text("لیست روز $d حذف بشه؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        PicksStore.deleteDay(context, mode, d)
                        deleteTarget = null
                        reload()
                    }
                ) { Text("حذف", color = HRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("لغو") }
            }
        )
    }

    // ---------- دیالوگ حذف همه ----------
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("حذف همه تاریخچه") },
            text = { Text("همه روزهای ذخیره‌شده حذف می‌شن. این کار قابل برگشت نیست!") },
            confirmButton = {
                TextButton(
                    onClick = {
                        PicksStore.clearAll(context, mode)
                        showClearDialog = false
                        reload()
                    }
                ) { Text("حذف همه", color = HRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("لغو") }
            }
        )
    }
}

// ---------- کارت یک روز ----------

@Composable
private fun DayCard(
    day: DayPicks,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(day.date, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${day.picks.size} سیگنال | بهترین: ${day.picks.maxOfOrNull { it.score } ?: 0}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(if (expanded) "بستن" else "مشاهده")
                }
                TextButton(onClick = onDelete) { Text("حذف", color = HRed) }
            }

            if (expanded) {
                day.picks.take(20).forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (p.side == "PUMP") "🚀" else "🩸", fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            p.symbol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (p.golden) Text("🏆", fontSize = 12.sp)
                        Text(
                            "${p.score}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (p.side == "PUMP") HGreen else HRed
                        )
                    }
                }
            }
        }
    }
}
