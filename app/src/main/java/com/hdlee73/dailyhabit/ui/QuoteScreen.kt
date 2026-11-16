package com.hdlee73.dailyhabit.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.CalendarRepository
import com.hdlee73.dailyhabit.data.Quotes
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 일반용 첫 화면: 오늘의 명언 */
@Composable
fun QuoteScreen(onNavigate: (Section) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val todayIndex = remember { Quotes.indexFor(today) }
    var index by rememberSaveable { mutableIntStateOf(todayIndex) }
    val quote = Quotes.all[index.mod(Quotes.all.size)]

    val db = remember { AppDatabase.get(context) }
    val routines by db.routineDao().observeActive().collectAsState(initial = emptyList())
    val checks by db.routineDao().observeChecks().collectAsState(initial = emptyList())
    val todos by db.todoDao().observeAll().collectAsState(initial = emptyList())
    val calendar = remember { CalendarRepository(context) }
    var eventCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        if (calendar.hasPermission()) eventCount = calendar.groupByDay(calendar.events(today, 1), today, 1)[today]?.size ?: 0
    }
    val todayRoutines = routines.filter { it.activeOn(today.dayOfWeek) }
    val doneRoutines = todayRoutines.count { r -> checks.any { it.routineId == r.id && it.epochDay == today.toEpochDay() } }
    val todayTodos = todos.count { !it.done && it.dueEpochDay != null && it.dueEpochDay <= today.toEpochDay() }

    val text = "${quote.ko}\n${quote.en}\n— ${quote.author}"

    TopScrollColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(
                "${today.year}년 · ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${today.monthValue}월 ${today.dayOfMonth}일", style = MaterialTheme.typography.displaySmall)
        }
        TodaySummaryRow(eventCount, todayTodos, doneRoutines, todayRoutines.size, onNavigate)

        SoftCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), bordered = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(if (index == todayIndex) "오늘의 명언" else "명언 ${index.mod(Quotes.all.size) + 1} / ${Quotes.all.size}",
                    Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.FormatQuote, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(12.dp))
            Text(quote.ko, style = ScriptureStyle.copy(fontSize = 21.sp, lineHeight = 34.sp))
            Spacer(Modifier.height(14.dp))
            Text(
                quote.en,
                fontFamily = FontFamily.Default,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "— ${quote.author}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.End),
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { index-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 명언") }
            IconButton(onClick = { index++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 명언") }
            if (index != todayIndex) TextButton(onClick = { index = todayIndex }) { Text("오늘의 명언") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                val cm = context.getSystemService(ClipboardManager::class.java)
                cm?.setPrimaryClip(ClipData.newPlainText("명언", text))
                Toast.makeText(context, "복사했어요", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Filled.ContentCopy, "복사") }
            IconButton(onClick = {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "명언 공유")
                )
            }) { Icon(Icons.Filled.Share, "공유") }
        }
    }
}
