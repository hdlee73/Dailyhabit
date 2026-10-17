package com.hdlee73.dailyhabit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.Routine
import com.hdlee73.dailyhabit.data.RoutineCheck
import com.hdlee73.dailyhabit.data.RoutineStat
import com.hdlee73.dailyhabit.data.RoutineStats
import com.hdlee73.dailyhabit.notify.Reminders
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private val EMOJIS = listOf(
    "🙏", "📿", "📖", "⛪", "✝️", "🕯️", "💧", "🏃", "🧘", "🚶", "💪", "🥗",
    "💊", "😴", "🌅", "📚", "✍️", "🎹", "🧹", "👨‍👩‍👧", "💌", "🌱", "☕", "✨",
)

@Composable
fun RoutineScreen(openEditor: Boolean = false, onEditorOpened: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).routineDao() }
    val routines by dao.observeActive().collectAsState(initial = emptyList())
    val checks by dao.observeChecks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    // 첫 탭이 아니면 뒤로 가기로 첫 탭으로 돌아간다
    ScreenBackHandler(enabled = tab != 0) { tab = 0 }
    var editing by remember { mutableStateOf<Routine?>(null) }
    var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val settings = remember { com.hdlee73.dailyhabit.data.AppSettings(context) }
    var compact by remember { mutableStateOf(settings.routineCompact) }

    LaunchedEffect(openEditor) {
        if (openEditor) {
            tab = 0
            creating = true
            onEditorOpened()
        }
    }

    val checkMap: Map<Long, Set<Long>> = remember(checks) {
        checks.groupBy(RoutineCheck::routineId).mapValues { (_, v) -> v.map { it.epochDay }.toSet() }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SegmentedTabs(
                listOf("오늘의 루틴", "통계"),
                tab,
                { tab = it },
                Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp),
            )
            if (tab == 0) {
                TodayRoutines(
                    routines, checkMap, today, compact,
                    onToggleCompact = { compact = !compact; settings.routineCompact = compact },
                    onToggle = { r, day -> scope.launch { dao.toggle(r.id, day.toEpochDay()) } },
                    onEdit = { editing = it },
                )
            } else {
                RoutineStatsView(routines, checkMap, today)
            }
        }
        if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("루틴 추가") },
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            )
        }
    }

    if (creating || editing != null) {
        RoutineEditorSheet(
            initial = editing ?: Routine(title = "", createdEpochDay = today.toEpochDay(), sortOrder = routines.size),
            isNew = editing == null,
            onDismiss = { creating = false; editing = null },
            onSave = { r ->
                scope.launch {
                    val saved = if (r.id == 0L) r.copy(id = dao.insert(r)) else r.also { dao.update(it) }
                    Reminders.scheduleRoutine(context, saved)
                }
                creating = false; editing = null
            },
            onDelete = { r ->
                scope.launch {
                    Reminders.cancelRoutine(context, r.id)
                    dao.delete(r.id)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun TodayRoutines(
    routines: List<Routine>,
    checks: Map<Long, Set<Long>>,
    today: LocalDate,
    compact: Boolean,
    onToggleCompact: () -> Unit,
    onToggle: (Routine, LocalDate) -> Unit,
    onEdit: (Routine) -> Unit,
) {
    val todays = routines.filter { it.activeOn(today.dayOfWeek) }
    val resting = routines - todays.toSet()
    val done = todays.count { today.toEpochDay() in checks[it.id].orEmpty() }

    TopLazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SoftCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val p = if (todays.isEmpty()) 0f else done.toFloat() / todays.size
                    ProgressRing(p, size = 76.dp, stroke = 8.dp, track = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.padding(start = 18.dp)) {
                        Text(
                            when {
                                routines.isEmpty() -> "첫 루틴을 만들어 보세요"
                                todays.isEmpty() -> "오늘은 쉬는 날이에요"
                                done == todays.size -> "오늘 루틴 완료! 🎉"
                                done == 0 -> "오늘의 루틴을 시작해요"
                                else -> "잘하고 있어요, 조금만 더!"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (todays.isEmpty()) "예정된 루틴 없음" else "${todays.size}개 중 ${done}개 완료",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.weight(1f))
                    androidx.compose.material3.IconButton(onClick = onToggleCompact) {
                        Icon(
                            if (compact) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                            contentDescription = if (compact) "자세히 보기" else "간단히 보기",
                        )
                    }
                }
            }
        }
        if (routines.isEmpty()) {
            item {
                EmptyState("🌱", "매일 반복할 습관을 등록해 보세요", "기도, 성경 읽기, 운동처럼 매일 하는 일을 루틴으로 만들면\n알림을 받고 체크하고 통계로 확인할 수 있어요.")
            }
        }
        if (compact) {
            // 간단히 보기: 한 줄에 두 개, 제목과 체크만
            items(todays.chunked(2), key = { row -> "c${row.first().id}" }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { r ->
                        CompactRoutine(r, checks[r.id].orEmpty(), today, false, { onToggle(r, today) }, { onEdit(r) }, Modifier.weight(1f))
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
            if (resting.isNotEmpty()) {
                item { SectionLabel("오늘 쉬는 루틴", Modifier.padding(top = 12.dp)) }
                items(resting.chunked(2), key = { row -> "r${row.first().id}" }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { r ->
                            CompactRoutine(r, checks[r.id].orEmpty(), today, true, { onToggle(r, today) }, { onEdit(r) }, Modifier.weight(1f))
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        } else {
            items(todays, key = { it.id }) { r ->
                RoutineCard(r, checks[r.id].orEmpty(), today, onToggle = { onToggle(r, it) }, onClick = { onEdit(r) })
            }
            if (resting.isNotEmpty()) {
                item { SectionLabel("오늘 쉬는 루틴", Modifier.padding(top = 12.dp)) }
                items(resting, key = { it.id }) { r ->
                    RoutineCard(r, checks[r.id].orEmpty(), today, onToggle = { onToggle(r, it) }, onClick = { onEdit(r) }, resting = true)
                }
            }
        }
    }
}

@Composable
private fun CompactRoutine(
    r: Routine,
    checked: Set<Long>,
    today: LocalDate,
    resting: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = routineColor(r.color)
    val isDone = today.toEpochDay() in checked
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (isDone) color.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            r.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = if (resting) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        if (!resting) CheckCircle(isDone, color, size = 30.dp, onClick = onToggle)
    }
}

private fun daysLabel(days: Int): String = when (days) {
    Routine.ALL_DAYS -> "매일"
    0b0011111 -> "평일"
    0b1100000 -> "주말"
    else -> WEEKDAY_SHORT.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" ")
}

@Composable
private fun RoutineCard(
    r: Routine,
    checked: Set<Long>,
    today: LocalDate,
    onToggle: (LocalDate) -> Unit,
    onClick: () -> Unit,
    resting: Boolean = false,
) {
    val color = routineColor(r.color)
    val isDone = today.toEpochDay() in checked
    val streak = remember(checked, r) { RoutineStats.stat(r, checked, today).currentStreak }
    SoftCard(onClick = onClick, color = if (isDone) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(r.emoji, color)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(daysLabel(r.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.reminderEnabled) {
                        Icon(Icons.Filled.Notifications, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMinute(r.reminderMinute), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r.kind != 0) Pill("자동 체크", MaterialTheme.colorScheme.tertiary)
                    if (streak > 0) Pill("🔥 ${streak}일", MaterialTheme.colorScheme.secondary)
                }
            }
            if (!resting) CheckCircle(isDone, color, size = 40.dp) { onToggle(today) }
        }
        // 최근 7일 기록 (지난 날도 눌러서 고칠 수 있음)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 6 downTo 0) {
                val d = today.minusDays(i.toLong())
                val active = r.activeOn(d.dayOfWeek)
                val on = d.toEpochDay() in checked
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        WEEKDAY_SHORT[d.dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d == today) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .then(
                                when {
                                    on -> Modifier.background(color)
                                    active -> Modifier.border(1.5.dp, color.copy(alpha = 0.35f), CircleShape)
                                    else -> Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                                }
                            )
                            .clickable(enabled = active || on) { onToggle(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${d.dayOfMonth}",
                            fontSize = 10.sp,
                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// ───────────── 통계 ─────────────

@Composable
private fun RoutineStatsView(routines: List<Routine>, checks: Map<Long, Set<Long>>, today: LocalDate) {
    if (routines.isEmpty()) {
        EmptyState("📊", "아직 통계가 없어요", "루틴을 만들고 체크하면 연속 기록과 달성률을 보여드려요.")
        return
    }
    val stats = remember(routines, checks) { routines.map { RoutineStats.stat(it, checks[it.id].orEmpty(), today) } }
    val weekly = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to RoutineStats.dailyRate(routines, checks, it) }
    val weekAvg = weekly.mapNotNull { it.second }.let { if (it.isEmpty()) 0f else it.average().toFloat() }
    val monthAvg = (0 until 30).mapNotNull { RoutineStats.dailyRate(routines, checks, today.minusDays(it.toLong())) }
        .let { if (it.isEmpty()) 0f else it.average().toFloat() }
    val bestStreak = stats.maxOfOrNull { it.bestStreak } ?: 0
    val totalChecks = stats.sumOf { it.totalChecks }

    TopLazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SoftCard {
                SectionLabel("한눈에 보기")
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock("${(weekAvg * 100).roundToInt()}%", "최근 7일", color = MaterialTheme.colorScheme.primary)
                    StatBlock("${(monthAvg * 100).roundToInt()}%", "최근 30일")
                    StatBlock("${bestStreak}일", "최고 연속", color = MaterialTheme.colorScheme.secondary)
                    StatBlock("$totalChecks", "누적 체크")
                }
            }
        }
        item {
            SoftCard {
                SectionLabel("최근 7일 달성률")
                WeekBars(weekly, today, Modifier.padding(top = 16.dp))
            }
        }
        item {
            SoftCard {
                SectionLabel("최근 12주 기록")
                Heatmap(routines, checks, today, Modifier.padding(top = 14.dp))
            }
        }
        item { SectionLabel("루틴별", Modifier.padding(top = 8.dp)) }
        items(stats, key = { it.routine.id }) { s -> RoutineStatCard(s, checks[s.routine.id].orEmpty(), today) }
    }
}

@Composable
private fun WeekBars(data: List<Pair<LocalDate, Float?>>, today: LocalDate, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainer
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        data.forEach { (day, rate) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    rate?.let { "${(it * 100).roundToInt()}" } ?: "–",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Canvas(Modifier.padding(vertical = 6.dp).width(22.dp).height(96.dp)) {
                    val r = CornerRadius(8.dp.toPx())
                    drawRoundRect(track, cornerRadius = r)
                    val h = size.height * (rate ?: 0f)
                    if (h > 0f) drawRoundRect(
                        if (day == today) primary else primary.copy(alpha = 0.55f),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h),
                        cornerRadius = r,
                    )
                }
                Text(
                    WEEKDAY_SHORT[day.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == today) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** 깃허브 잔디처럼 12주 x 7일 */
@Composable
private fun Heatmap(routines: List<Routine>, checks: Map<Long, Set<Long>>, today: LocalDate, modifier: Modifier = Modifier) {
    val weeks = 12
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainer
    // 이번 주 월요일 기준으로 시작
    val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val start = thisMonday.minusWeeks((weeks - 1).toLong())
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAY_SHORT.forEachIndexed { i, d ->
                Box(Modifier.height(18.dp), contentAlignment = Alignment.Center) {
                    Text(if (i % 2 == 0) d else "", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp), textAlign = TextAlign.Center)
                }
            }
        }
        for (w in 0 until weeks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 0 until 7) {
                    val day = start.plusWeeks(w.toLong()).plusDays(d.toLong())
                    val rate = if (day.isAfter(today)) null else RoutineStats.dailyRate(routines, checks, day)
                    Box(
                        Modifier
                            .height(18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    day.isAfter(today) -> Color.Transparent
                                    rate == null || rate == 0f -> empty
                                    else -> primary.copy(alpha = 0.25f + 0.75f * rate)
                                }
                            )
                            .then(if (day == today) Modifier.border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)) else Modifier)
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text("적음 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0f, 0.33f, 0.66f, 1f).forEach { a ->
            Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(RoundedCornerShape(3.dp))
                .background(if (a == 0f) empty else primary.copy(alpha = 0.25f + 0.75f * a)))
        }
        Text(" 많음", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RoutineStatCard(s: RoutineStat, checked: Set<Long>, today: LocalDate) {
    val color = routineColor(s.routine.color)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(s.routine.emoji, color, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(s.routine.title, style = MaterialTheme.typography.titleMedium)
                Text(daysLabel(s.routine.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("🔥 ${s.currentStreak}일", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Text("최고 ${s.bestStreak}일", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RateRow("7일", s.week, color)
            RateRow("30일", s.month, color)
        }
        // 이번 달 달력
        MonthDots(s.routine, checked, today, color, Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun RateRow(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDots(r: Routine, checked: Set<Long>, today: LocalDate, color: Color, modifier: Modifier = Modifier) {
    val first = today.withDayOfMonth(1)
    Column(modifier) {
        Text("${today.monthValue}월", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp), maxItemsInEachRow = 16) {
            for (i in 0 until today.lengthOfMonth()) {
                val d = first.plusDays(i.toLong())
                val on = d.toEpochDay() in checked
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                on -> color
                                d.isAfter(today) || !r.activeOn(d.dayOfWeek) -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                )
            }
        }
    }
}

// ───────────── 편집 ─────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditorSheet(
    initial: Routine,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit,
    onDelete: (Routine) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(initial.title) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var color by remember { mutableStateOf(initial.color) }
    var days by remember { mutableStateOf(initial.days) }
    var reminder by remember { mutableStateOf(initial.reminderEnabled) }
    var minute by remember { mutableStateOf(initial.reminderMinute) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(emoji, routineColor(color), size = 52.dp)
                    Text(if (isNew) "새 루틴" else "루틴 수정", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 14.dp))
                }
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("루틴 이름") },
                    placeholder = { Text("예: 묵주기도, 성경 한 장 읽기") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("아이콘")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 8) {
                        EMOJIS.forEach { e ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (e == emoji) routineColor(color).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 20.sp) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("색상")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        RoutinePalette.forEachIndexed { i, c ->
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .then(if (i == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { color = i }
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("반복 요일")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        WEEKDAY_SHORT.forEachIndexed { i, d ->
                            val on = days and (1 shl i) != 0
                            val c = routineColor(color)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (on) c else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { days = days xor (1 shl i) },
                                contentAlignment = Alignment.Center,
                            ) { Text(d, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(days == Routine.ALL_DAYS, { days = Routine.ALL_DAYS }, { Text("매일") })
                        FilterChip(days == 0b0011111, { days = 0b0011111 }, { Text("평일") })
                        FilterChip(days == 0b1100000, { days = 0b1100000 }, { Text("주말") })
                    }
                }
            }
            item {
                SoftCard(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("알림", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (reminder) "${formatMinute(minute)}에 알려드려요" else "알림 없음",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = reminder, onCheckedChange = { reminder = it })
                    }
                    if (reminder) {
                        OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Text("시간 변경 · ${formatMinute(minute)}")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isNew) {
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("삭제") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(
                        enabled = title.isNotBlank() && days != 0,
                        onClick = {
                            onSave(initial.copy(title = title.trim(), emoji = emoji, color = color, days = days,
                                reminderEnabled = reminder, reminderMinute = minute))
                        },
                    ) { Text("저장") }
                }
            }
        }
    }

    if (pickTime) TimePickDialog(minute, onDismiss = { pickTime = false }) { minute = it }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("루틴 삭제") },
            text = { Text("‘${initial.title}’ 루틴과 지금까지의 체크 기록이 모두 삭제돼요.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
