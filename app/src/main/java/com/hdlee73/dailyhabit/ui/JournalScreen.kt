package com.hdlee73.dailyhabit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.DIARY_MOODS
import com.hdlee73.dailyhabit.data.DiaryEntry
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val MAX_LEN = 200

private val Prompts = listOf(
    "오늘 가장 좋았던 순간은?",
    "오늘 감사한 일 하나",
    "오늘 새로 알게 된 것",
    "오늘의 나에게 한마디",
    "오늘 웃었던 일",
    "내일 꼭 하고 싶은 것",
    "오늘 고마웠던 사람",
)

private fun weekdayShort(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

private fun streakOf(days: Set<Long>, today: Long): Int {
    var d = if (today in days) today else today - 1
    var n = 0
    while (d in days) { n++; d-- }
    return n
}

@Composable
fun JournalScreen(openEditor: Boolean = false, onEditorOpened: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).diaryDao() }
    val entries by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val byDay = remember(entries) { entries.associateBy { it.epochDay } }
    val todayEntry = byDay[todayDay]

    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var onlyGratitude by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(openEditor) {
        if (openEditor) {
            runCatching { focus.requestFocus() }
            onEditorOpened()
        }
    }

    // 오늘 쓰기 칸: 저장된 내용이 바뀌면 다시 채운다
    var text by remember(todayEntry?.updatedAt) { mutableStateOf(todayEntry?.text.orEmpty()) }
    var mood by remember(todayEntry?.updatedAt) { mutableIntStateOf(todayEntry?.mood ?: 0) }
    val dirty = text.trim() != todayEntry?.text.orEmpty() || mood != (todayEntry?.mood ?: 0)

    val q = query.trim()
    val shown = entries
        .filter { !onlyGratitude || GratitudeWords.containsMatchIn(it.text) }
        .filter { q.isEmpty() || it.text.contains(q, ignoreCase = true) }
    val grouped = shown.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
    val thisMonth = YearMonth.from(today)
    val monthCount = entries.count { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == thisMonth }
    val streak = streakOf(byDay.keys, todayDay)
    val memories = (1..5).mapNotNull { y ->
        val d = runCatching { today.minusYears(y.toLong()) }.getOrNull() ?: return@mapNotNull null
        byDay[d.toEpochDay()]?.let { y to it }
    }

    TopLazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${formatKoreanDate(today)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Default,
                        )
                        Text(
                            if (todayEntry == null) "오늘의 한 줄을 남겨 보세요" else "오늘의 한 줄이 저장돼 있어요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MoodRow(mood, Modifier.padding(top = 12.dp)) { mood = it }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_LEN) text = it },
                    placeholder = { Text(Prompts[today.dayOfYear % Prompts.size]) },
                    minLines = 2,
                    maxLines = 5,
                    supportingText = { Text("${text.length}/$MAX_LEN") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focus),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickDate = true }) { Text("다른 날 쓰기") }
                    Box(Modifier.weight(1f))
                    Button(
                        enabled = text.isNotBlank() && dirty,
                        onClick = { scope.launch { dao.upsert(DiaryEntry(todayDay, text.trim(), mood)) } },
                    ) { Text(if (todayEntry == null) "저장" else "수정 저장") }
                }
            }
        }

        if (entries.isNotEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBubble("연속 기록", "${streak}일", Modifier.weight(1f))
                StatBubble("이번 달", "${monthCount}일", Modifier.weight(1f))
                StatBubble("전체", "${entries.size}개", Modifier.weight(1f))
            }
        }

        items(memories, key = { "m${it.first}" }) { (years, e) ->
            SoftCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), bordered = false, onClick = { editing = LocalDate.ofEpochDay(e.epochDay) }) {
                SectionLabel("${years}년 전 오늘", color = MaterialTheme.colorScheme.primary)
                Text(
                    (DIARY_MOODS.getOrNull(e.mood - 1)?.let { "$it  " } ?: "") + e.text,
                    fontFamily = FontFamily.Default,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (entries.isNotEmpty()) item {
            androidx.compose.material3.FilterChip(
                selected = onlyGratitude,
                onClick = { onlyGratitude = !onlyGratitude },
                label = { Text("🙏 감사 일기 모음") },
            )
        }

        if (entries.size > 3) item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("지난 일기 찾기") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "지우기") } }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (entries.isEmpty()) item {
            EmptyState("📖", "아직 쓴 일기가 없어요", "하루에 한 줄이면 충분해요. 위 칸에 오늘을 적어 보세요.")
        } else if (shown.isEmpty()) item {
            if (onlyGratitude) EmptyState("🙏", "아직 감사 일기가 없어요", "일기에 ‘감사’나 ‘고마워’를 적으면 여기에 모여요.")
            else EmptyState("🔍", "찾는 일기가 없어요", "다른 낱말로 찾아 보세요.")
        }

        grouped.forEach { (ym, list) ->
            item(key = "h$ym") {
                Text(
                    "${ym.year}년 ${ym.monthValue}월 · ${list.size}개",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                )
            }
            items(list, key = { it.epochDay }) { e ->
                val d = LocalDate.ofEpochDay(e.epochDay)
                SoftCard(onClick = { editing = d }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.size(width = 44.dp, height = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.dayOfMonth}", fontFamily = FontFamily.Default, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            Text(weekdayShort(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            e.text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        DIARY_MOODS.getOrNull(e.mood - 1)?.let { Text(it, fontSize = 22.sp) }
                    }
                }
            }
        }
    }

    if (pickDate) {
        DatePickDialog(today, onDismiss = { pickDate = false }) { picked ->
            pickDate = false
            if (picked.isAfter(today)) android.widget.Toast.makeText(context, "오늘 이후의 날짜는 쓸 수 없어요", android.widget.Toast.LENGTH_SHORT).show()
            else editing = picked
        }
    }
    editing?.let { day ->
        DiaryEditDialog(
            day = day,
            initial = byDay[day.toEpochDay()],
            onDismiss = { editing = null },
            onSave = { t, m ->
                scope.launch { dao.upsert(DiaryEntry(day.toEpochDay(), t, m)) }
                editing = null
            },
            onDelete = {
                scope.launch { dao.delete(day.toEpochDay()) }
                editing = null
            },
        )
    }
}

@Composable
private fun StatBubble(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MoodRow(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DIARY_MOODS.forEachIndexed { i, emoji ->
            val on = selected == i + 1
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onSelect(if (on) 0 else i + 1) },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 24.sp) }
        }
    }
}

@Composable
private fun DiaryEditDialog(
    day: LocalDate,
    initial: DiaryEntry?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var mood by remember { mutableIntStateOf(initial?.mood ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${formatKoreanDate(day)}") },
        text = {
            Column {
                MoodRow(mood) { mood = it }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_LEN) text = it },
                    placeholder = { Text("이날의 한 줄") },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = { Text("${text.length}/$MAX_LEN") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim(), mood) }) { Text("저장") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { confirmDelete = true }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 일기를 지울까요?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
