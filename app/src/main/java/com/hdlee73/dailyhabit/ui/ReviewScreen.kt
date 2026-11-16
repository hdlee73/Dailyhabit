package com.hdlee73.dailyhabit.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.BookStatus
import com.hdlee73.dailyhabit.data.DIARY_MOODS
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** 감사 일기로 보는 낱말 */
val GratitudeWords = Regex("감사|고마|고맙")

private fun inMonth(epochDay: Long, month: YearMonth) = YearMonth.from(LocalDate.ofEpochDay(epochDay)) == month

@Composable
fun ReviewScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val diary by db.diaryDao().observeAll().collectAsState(initial = emptyList())
    val routines by db.routineDao().observeActive().collectAsState(initial = emptyList())
    val checks by db.routineDao().observeChecks().collectAsState(initial = emptyList())
    val books by db.bookDao().observeBooks().collectAsState(initial = emptyList())
    val notes by db.bookDao().observeNotes().collectAsState(initial = emptyList())
    val places by db.restaurantDao().observeAll().collectAsState(initial = emptyList())
    val visits by db.visitDao().observeAll().collectAsState(initial = emptyList())
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }

    // ── 기분
    val monthDiary = diary.filter { inMonth(it.epochDay, month) }.sortedBy { it.epochDay }
    val moodCounts = DIARY_MOODS.indices.map { i -> monthDiary.count { it.mood == i + 1 } }
    val topMood = moodCounts.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
    val gratitude = monthDiary.filter { GratitudeWords.containsMatchIn(it.text) }

    // ── 루틴 달성률
    val lastDay = when {
        month.isAfter(YearMonth.from(today)) -> 0
        month == YearMonth.from(today) -> today.dayOfMonth
        else -> month.lengthOfMonth()
    }
    val checkSet = remember(checks) { checks.map { it.routineId to it.epochDay }.toSet() }
    data class RoutineRate(val title: String, val emoji: String, val done: Int, val total: Int)
    val rates = routines.mapNotNull { r ->
        var done = 0
        var total = 0
        for (d in 1..lastDay) {
            val date = month.atDay(d)
            if (r.createdEpochDay <= date.toEpochDay() && r.activeOn(date.dayOfWeek)) {
                total++
                if ((r.id to date.toEpochDay()) in checkSet) done++
            }
        }
        if (total == 0) null else RoutineRate(r.title, r.emoji, done, total)
    }
    val totalDone = rates.sumOf { it.done }
    val totalAll = rates.sumOf { it.total }
    val routinePercent = if (totalAll == 0) 0f else totalDone.toFloat() / totalAll

    // ── 책
    val finished = books.filter {
        it.status == BookStatus.DONE.ordinal && it.endEpochDay != null && inMonth(it.endEpochDay, month)
    }
    val monthNotes = notes.count { n ->
        YearMonth.from(java.time.Instant.ofEpochMilli(n.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()) == month
    }

    // ── 맛집
    val monthVisits = visits.filter { inMonth(it.epochDay, month) }
    val placeById = places.associateBy { it.id }
    val visitedPlaces = monthVisits.groupBy { it.restaurantId }
        .mapNotNull { (id, list) -> placeById[id]?.let { it to list.size } }
        .sortedByDescending { it.second }

    fun shareText(): String = buildString {
        append("📅 ${month.year}년 ${month.monthValue}월 돌아보기\n")
        append("\n😊 기분: 일기 ${monthDiary.size}일")
        topMood?.let { append(" · 가장 많은 기분 ${DIARY_MOODS[it]} ${moodCounts[it]}일") }
        append("\n🔁 루틴 달성률: ${(routinePercent * 100).roundToInt()}% ($totalDone/$totalAll)")
        append("\n📚 읽은 책 ${finished.size}권")
        finished.forEach { append("\n  • ").append(it.title) }
        append("\n🍽️ 다녀온 맛집 ${visitedPlaces.size}곳, ${monthVisits.size}번")
        visitedPlaces.forEach { (p, n) -> append("\n  • ${p.name}" + if (n > 1) " ×$n" else "") }
    }

    TopScrollColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달") }
            Text(
                "${month.year}년 ${month.monthValue}월",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { month = month.plusMonths(1) },
                enabled = month.isBefore(YearMonth.from(today)),
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
        }

        // 기분
        SoftCard {
            SectionLabel("기분", color = MaterialTheme.colorScheme.primary)
            if (monthDiary.isEmpty()) {
                Text("이 달에 쓴 한 줄 일기가 없어요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text(
                    "일기 ${monthDiary.size}일" + (topMood?.let { " · 가장 많은 기분 ${DIARY_MOODS[it]}" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                val maxCount = (moodCounts.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).height(86.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DIARY_MOODS.forEachIndexed { i, emoji ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${moodCounts[i]}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(
                                Modifier
                                    .padding(vertical = 3.dp)
                                    .width(22.dp)
                                    .height((6 + 36 * moodCounts[i] / maxCount).dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (moodCounts[i] == 0) MaterialTheme.colorScheme.surfaceContainerHighest
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    ),
                            )
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        // 루틴
        SoftCard {
            SectionLabel("루틴 달성률", color = MaterialTheme.colorScheme.primary)
            if (rates.isEmpty()) {
                Text("이 달에 해당하는 루틴이 없어요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(routinePercent, size = 72.dp, stroke = 8.dp, track = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text("${(routinePercent * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "${totalAll}번 중 ${totalDone}번 했어요",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                rates.sortedByDescending { it.done.toFloat() / it.total }.forEach { r ->
                    Column(Modifier.padding(top = 12.dp)) {
                        Row {
                            Text("${r.emoji} ${r.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${r.done}/${r.total}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { r.done.toFloat() / r.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
            }
        }

        // 책
        SoftCard {
            SectionLabel("읽은 책", color = MaterialTheme.colorScheme.primary)
            if (finished.isEmpty()) {
                Text("이 달에 다 읽은 책이 없어요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("${finished.size}권 · ${finished.sumOf { it.totalPages }}쪽", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
                finished.forEach { b ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.title, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Default, modifier = Modifier.weight(1f))
                        if (b.rating > 0) StarRow(b.rating, 14.dp)
                    }
                }
            }
            if (monthNotes > 0) Text("남긴 밑줄·메모 ${monthNotes}개", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }

        // 맛집
        SoftCard {
            SectionLabel("다녀온 맛집", color = MaterialTheme.colorScheme.primary)
            if (visitedPlaces.isEmpty()) {
                Text("이 달에 기록한 방문이 없어요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("${visitedPlaces.size}곳 · ${monthVisits.size}번 방문", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp))
                visitedPlaces.forEach { (p, n) ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (n > 1) Text("×$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // 감사 일기 모음
        SoftCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), bordered = false) {
            SectionLabel("🙏 감사한 순간", color = MaterialTheme.colorScheme.primary)
            if (gratitude.isEmpty()) {
                Text("‘감사’, ‘고마워’가 들어간 일기가 여기에 모여요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } else {
                gratitude.forEach { e ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(formatKoreanDate(LocalDate.ofEpochDay(e.epochDay)), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.text, fontFamily = FontFamily.Default, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText()),
                        "이 달 돌아보기 보내기",
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("이 달 정리 보내기")
        }
    }
}
