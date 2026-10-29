package com.hdlee73.dailyhabit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.CalendarRepository
import com.hdlee73.dailyhabit.data.Gospel
import com.hdlee73.dailyhabit.data.GospelRepository
import com.hdlee73.dailyhabit.data.Reading
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(onNavigate: (Section) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { GospelRepository(context) }
    val today = remember { LocalDate.now() }
    var gospel by remember { mutableStateOf(repo.cached(today)) }
    var loading by remember { mutableStateOf(gospel == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        repo.get(today, forceRefresh = reload > 0)
            .onSuccess { gospel = it }
            .onFailure { error = it.message ?: "알 수 없는 오류" }
        loading = false
    }

    // 오늘 요약
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

    TopScrollColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── 날짜 헤더
        val litColor = liturgicalColor(gospel?.liturgicalDay.orEmpty())
        Column {
            Text(
                "${today.year}년 · ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${today.monthValue}월 ${today.dayOfMonth}일", style = MaterialTheme.typography.displaySmall)
            gospel?.liturgicalDay?.takeIf { it.isNotBlank() }?.let { day ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(litColor))
                    Text(
                        day.replace(Regex("^\\([^)]*\\)\\s*"), ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        TodaySummaryRow(eventCount, todayTodos, doneRoutines, todayRoutines.size, onNavigate)

        // ── 오늘의 말씀 (제1독서 · 화답송 · 제2독서 · 복음 환호송 · 복음)
        val readings = gospel?.readings.orEmpty()
        val parts = listOf(ALL_PARTS) + readings.map { it.kind } + GOSPEL_PART
        var picked by rememberSaveable { mutableStateOf(ALL_PARTS) }
        val part = if (picked in parts) picked else ALL_PARTS
        SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("오늘의 말씀", Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                IconButton(onClick = { reload++ }, enabled = !loading, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "새로고침", modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                loading && gospel == null -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                gospel != null -> {
                    if (readings.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            parts.forEach { k -> FilterChip(part == k, { picked = k }, { Text(k) }) }
                        }
                    }
                    val shown = if (part == ALL_PARTS) readings else readings.filter { it.kind == part }
                    shown.forEach { r ->
                        ReadingBody(r)
                        HorizontalDivider(Modifier.padding(vertical = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (part == ALL_PARTS || part == GOSPEL_PART) {
                        if (readings.isNotEmpty()) SectionLabel(GOSPEL_PART, color = MaterialTheme.colorScheme.secondary)
                        GospelBody(gospel!!)
                    } else if (shown.isNotEmpty()) {
                        TextButton(onClick = { picked = GOSPEL_PART }) { Text("복음 보기") }
                    }
                }
                else -> Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        "말씀을 불러오지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { reload++ }) { Text("다시 시도") }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.sourceUrl(today)))) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("매일미사에서 보기")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

    }
}

private val VERSE = Regex("(?<=^|\\s)(\\d{1,3})(?=\\s)")

/** 절 번호를 작은 위첨자로 */
private fun verseAnnotated(body: String, verseColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    VERSE.findAll(body).forEach { m ->
        append(body.substring(last, m.range.first))
        withStyle(SpanStyle(fontSize = 11.sp, color = verseColor, baselineShift = BaselineShift(0.35f), fontFamily = FontFamily.SansSerif)) {
            append(m.value)
        }
        last = m.range.last + 1
    }
    append(body.substring(last))
}

private const val ALL_PARTS = "전체"
private const val GOSPEL_PART = "복음"

/**
 * 독서 · 화답송 · 복음 환호송. 매일미사 본문의 표시를 살려서 보여 준다.
 * <주제> · ▥ 출처 · ◎ 함께 응답 · ○ 선창 · <또는> · 주님의 말씀입니다.
 */
@Composable
private fun ReadingBody(r: Reading) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lines = remember(r.text) { r.text.lines().filter { it.isNotBlank() } }
    // 화답송·환호송은 첫 줄이 성경 출처
    val isReading = r.kind.endsWith("독서")
    Column {
        SectionLabel(r.kind, color = secondary)
        var i = 0
        if (!isReading && lines.isNotEmpty()) {
            Text(lines[0], style = MaterialTheme.typography.titleMedium, color = primary, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            i = 1
        }
        val body = buildAnnotatedString {
            var first = true
            fun nl() { if (!first) append('\n'); first = false }
            lines.drop(i).forEach { raw ->
                when {
                    raw == "<또는>" -> {
                        nl(); append('\n')
                        withStyle(SpanStyle(color = muted, fontSize = 13.sp, fontFamily = FontFamily.SansSerif)) { append("또는") }
                    }
                    raw.startsWith("<") && raw.endsWith(">") -> {
                        nl()
                        withStyle(SpanStyle(color = muted, fontSize = 15.sp)) { append(raw.removeSurrounding("<", ">")) }
                    }
                    raw.startsWith("▥") -> {
                        nl()
                        val t = raw.removePrefix("▥").trim().replace(Regex("말씀입니다\\.\\s*"), "말씀입니다. ")
                        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) { append(t) }
                    }
                    raw.startsWith("주님의 말씀입니다") -> {
                        nl()
                        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) { append(raw) }
                    }
                    raw.startsWith("◎") || raw.startsWith("(◎") -> {
                        nl()
                        withStyle(SpanStyle(color = secondary, fontWeight = FontWeight.Bold)) { append(raw) }
                    }
                    raw.startsWith("○") -> {
                        nl()
                        withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold)) { append("○ ") }
                        append(raw.removePrefix("○").trim().replace(Regex("\\s*◎$"), ""))
                        if (raw.trimEnd().endsWith("◎")) withStyle(SpanStyle(color = secondary, fontWeight = FontWeight.Bold)) { append(" ◎") }
                    }
                    else -> { nl(); append(verseAnnotated(raw, secondary)) }
                }
            }
        }
        Text(body, style = ScriptureStyle, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun GospelBody(g: Gospel) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(g.reference, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        if (g.title.isNotBlank()) {
            Text(
                g.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
        }
        val verseColor = MaterialTheme.colorScheme.secondary
        Text(remember(g.body) { verseAnnotated(g.body, verseColor) }, style = ScriptureStyle)
        Text(
            "주님의 말씀입니다.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
