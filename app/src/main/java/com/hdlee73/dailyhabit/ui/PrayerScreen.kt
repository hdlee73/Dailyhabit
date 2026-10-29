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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.data.MonthlyPrayers
import com.hdlee73.dailyhabit.data.PrayerItem
import com.hdlee73.dailyhabit.data.Prayers
import java.time.LocalDate

@Composable
fun PrayerScreen(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openId?.let { Prayers.byId(it) }
    if (open != null) {
        ScreenBackHandler { openId = null }
        PrayerDetail(open, onBack = { openId = null }, modifier)
    } else {
        PrayerList(
            onOpen = {
                openId = it.id
                // 기도문을 열어 보면 '기도하기' 루틴을 체크한다
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.hdlee73.dailyhabit.data.FaithRoutines.markDone(context, com.hdlee73.dailyhabit.data.FaithRoutines.KIND_PRAYER)
                }
            },
            modifier,
        )
    }
}

@Composable
private fun PrayerList(onOpen: (PrayerItem) -> Unit, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val mystery = Prayers.mysteryFor(today)
    TopLazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 오늘의 묵주기도
        item {
            SoftCard(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                onClick = { Prayers.byId(Prayers.ROSARY_ID)?.let(onOpen) },
            ) {
                SectionLabel("오늘의 묵주기도", color = MaterialTheme.colorScheme.tertiary)
                Text(mystery.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "1단 · ${mystery.decades.first()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // 이달의 성월 (3·5·6·9·10·11월)
        MonthlyPrayers.forDate(today)?.let { m ->
            item {
                SoftCard(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    onClick = { onOpen(m.prayer) },
                ) {
                    SectionLabel("이달의 성월", color = MaterialTheme.colorScheme.primary)
                    Text(m.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        monthlyTitle(m.prayer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Prayers.categories.forEach { cat ->
            val list = Prayers.all.filter { it.category == cat }
            item(key = "h_$cat") { SectionLabel(cat, Modifier.padding(top = 12.dp, start = 4.dp)) }
            item(key = "g_$cat") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    list.forEachIndexed { i, p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(p) }
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                            )
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(p.title, style = MaterialTheme.typography.bodyLarge)
                                val preview = if (p.id == Prayers.ROSARY_ID) "오늘은 ${mystery.name}"
                                else if (p.category == MonthlyPrayers.CATEGORY) monthlyTitle(p)
                                else p.text.trimIndent().lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("[") }
                                    ?.trimStart('◎', '●', '○', '╋', '†', ' ', '(').orEmpty()
                                Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                        if (i < list.lastIndex) HorizontalDivider(Modifier.padding(start = 40.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** 성월 기도의 본문 첫 줄 [기도 제목] */
private fun monthlyTitle(p: PrayerItem): String =
    p.text.trimIndent().lineSequence().firstOrNull { it.startsWith("[") }?.removeSurrounding("[", "]").orEmpty()

@Composable
private fun PrayerDetail(p: PrayerItem, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    val style = ScriptureStyle.copy(fontSize = ScriptureStyle.fontSize * scale, lineHeight = ScriptureStyle.lineHeight * scale)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "목록으로") }
            Text("기도 목록", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { scale = (scale - 0.1f).coerceAtLeast(0.8f) }) { Icon(Icons.Filled.TextDecrease, "글자 작게") }
            IconButton(onClick = { scale = (scale + 0.1f).coerceAtMost(1.6f) }) { Icon(Icons.Filled.TextIncrease, "글자 크게") }
        }
        TopScrollColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 32.dp),
        ) {
            SectionLabel(p.category, color = MaterialTheme.colorScheme.secondary)
            Text(p.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            if (p.id == Prayers.ROSARY_ID) RosaryBody(style) else PrayerText(p.text.trimIndent(), style)
            if (p.note.isNotBlank()) {
                Text(
                    p.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(14.dp),
                )
            }
        }
    }
}

/** ◎ 이끄는 부분과 ● 응답 부분을 구분해서 표시 */
@Composable
private fun PrayerText(text: String, style: TextStyle) {
    val lead = MaterialTheme.colorScheme.primary
    val resp = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var responding = false
    val annotated = buildAnnotatedString {
        text.lines().forEachIndexed { i, raw ->
            if (i > 0) append('\n')
            when {
                raw.startsWith("╋") || raw.startsWith("○") || raw.startsWith("†") -> {
                    responding = false
                    withStyle(SpanStyle(color = lead, fontWeight = FontWeight.Bold)) { append(raw.take(1) + " ") }
                    append(raw.drop(1).trimStart())
                }
                raw.startsWith("●") || raw.startsWith("◎") -> {
                    responding = true
                    withStyle(SpanStyle(color = resp, fontWeight = FontWeight.Bold)) { append(raw.take(1) + " ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(raw.drop(1).trimStart()) }
                }
                raw.startsWith("[") && raw.endsWith("]") -> {
                    responding = false
                    withStyle(SpanStyle(color = lead, fontWeight = FontWeight.Bold, fontSize = style.fontSize * 0.85f)) {
                        append(raw.removeSurrounding("[", "]"))
                    }
                }
                raw.startsWith("(") && raw.endsWith(")") ->
                    withStyle(SpanStyle(color = muted, fontSize = style.fontSize * 0.85f)) { append(raw) }
                raw.isBlank() -> responding = false
                responding -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(raw) }
                else -> append(raw)
            }
        }
    }
    Text(annotated, style = style)
}

@Composable
private fun RosaryBody(style: TextStyle) {
    val today = LocalDate.now()
    val todays = Prayers.mysteryFor(today)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SoftCard(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)) {
            SectionLabel("바치는 순서", color = MaterialTheme.colorScheme.tertiary)
            Prayers.rosaryOrder.forEachIndexed { i, s ->
                Row(Modifier.padding(top = 8.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 10.dp, top = 2.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // 오늘의 신비를 먼저
        (listOf(todays) + Prayers.mysteries.filter { it != todays }).forEach { m ->
            val isToday = m == todays
            SoftCard(color = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (isToday) Pill("오늘", MaterialTheme.colorScheme.primary)
                }
                Text(m.days, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                m.decades.forEachIndexed { i, d ->
                    Row(Modifier.padding(top = 10.dp)) {
                        Text("${i + 1}단", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp, top = 3.dp))
                        Text(d, style = style.copy(fontSize = style.fontSize * 0.9f, lineHeight = style.lineHeight * 0.85f))
                    }
                }
            }
        }
    }
}
