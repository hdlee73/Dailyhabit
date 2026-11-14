package com.hdlee73.dailyhabit.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hdlee73.dailyhabit.data.AppSettings
import com.hdlee73.dailyhabit.data.ConfessionGuide
import com.hdlee73.dailyhabit.data.PrayerItem
import com.hdlee73.dailyhabit.data.Prayers
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfessionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val today = remember { LocalDate.now() }

    var lastEpochDay by remember { mutableStateOf(settings.lastConfessionEpochDay) }
    var checkedIds by remember { mutableStateOf(settings.confessionCheckedIds) }
    var memo by remember { mutableStateOf(settings.confessionMemo) }

    var showGuide by rememberSaveable { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmRecordToday by remember { mutableStateOf(false) }
    var confirmClearDate by remember { mutableStateOf(false) }
    var readingPrayer by remember { mutableStateOf<PrayerItem?>(null) }

    val lastDate = lastEpochDay?.let { LocalDate.ofEpochDay(it) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun saveDate(date: LocalDate, clearPrep: Boolean = false) {
        val day = date.toEpochDay()
        lastEpochDay = day
        settings.lastConfessionEpochDay = day
        if (clearPrep) {
            checkedIds = emptySet()
            settings.confessionCheckedIds = emptySet()
            memo = ""
            settings.confessionMemo = ""
        }
        toast("${date.monthValue}월 ${date.dayOfMonth}일을 고해성사 날짜로 기록했어요")
    }

    fun toggleItem(id: String) {
        val next = if (id in checkedIds) checkedIds - id else checkedIds + id
        checkedIds = next
        settings.confessionCheckedIds = next
    }

    fun clearPrep() {
        checkedIds = emptySet()
        settings.confessionCheckedIds = emptySet()
        memo = ""
        settings.confessionMemo = ""
        toast("양심성찰 체크와 메모를 비웠어요")
    }

    TopScrollColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── 마지막 고해성사 기록 카드
        SoftCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("마지막 고해성사", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                if (lastDate != null) {
                    val days = ConfessionGuide.daysSince(lastDate, today)
                    Pill(ConfessionGuide.formatElapsed(days), MaterialTheme.colorScheme.primary)
                }
            }
            if (lastDate != null) {
                val dow = lastDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
                Text(
                    "${lastDate.year}년 ${lastDate.monthValue}월 ${lastDate.dayOfMonth}일 ($dow)",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text(
                    "아직 기록된 성사일이 없어요",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "고해성사를 마친 날을 기록해 두면 마지막 성사일과 지난 기간을 한눈에 볼 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = {
                    if (checkedIds.isNotEmpty() || memo.isNotBlank()) confirmRecordToday = true
                    else saveDate(today)
                }) {
                    Icon(Icons.Filled.Check, null, Modifier.padding(end = 6.dp))
                    Text("오늘 성사 기록")
                }
                OutlinedButton(onClick = { pickDate = true }) { Text("날짜 고르기") }
                if (lastDate != null) {
                    TextButton(onClick = { confirmClearDate = true }) { Text("기록 지우기") }
                }
            }
        }

        // ── 고해성사 순서 · 기도문 (접기/펼치기)
        SoftCard(onClick = { showGuide = !showGuide }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("고해성사 순서 · 기도문", color = MaterialTheme.colorScheme.secondary)
                    Text(
                        if (showGuide) "고백할 때 바로 볼 수 있는 순서와 기도문" else "고백 시작 인사, 성사 순서, 통회·고백기도 보기",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Icon(
                    if (showGuide) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (showGuide) "접기" else "펼치기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = showGuide) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        ConfessionGuide.confessionOpening(lastDate, today),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(12.dp),
                    )
                    ConfessionGuide.steps.forEachIndexed { idx, (title, body) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                "${idx + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(22.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("examen" to "반성 기도", "contrition" to "통회기도", "confiteor" to "고백기도").forEach { (id, label) ->
                            OutlinedButton(onClick = { Prayers.byId(id)?.let { readingPrayer = it } }) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        // ── 양심성찰 체크리스트 헤더
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("양심성찰 체크리스트", Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
            if (checkedIds.isNotEmpty()) {
                Pill("${checkedIds.size}개 선택", MaterialTheme.colorScheme.primary, Modifier.padding(end = 6.dp))
            }
            if (checkedIds.isNotEmpty() || memo.isNotBlank()) {
                TextButton(
                    onClick = ::clearPrep,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) { Text("비우기") }
            }
        }

        // ── 십계명별 체크리스트
        ConfessionGuide.groups.forEach { group ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Pill(group.subtitle, MaterialTheme.colorScheme.secondary)
                }
                Column(Modifier.padding(top = 10.dp)) {
                    group.items.forEachIndexed { i, item ->
                        val checked = item.id in checkedIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { toggleItem(item.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckCircle(
                                checked = checked,
                                color = MaterialTheme.colorScheme.primary,
                                size = 24.dp,
                                onClick = { toggleItem(item.id) },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                                color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (i < group.items.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        // ── 고해할 내용 메모 (로컬 전용)
        SoftCard {
            SectionLabel("고해할 내용 메모", color = MaterialTheme.colorScheme.secondary)
            OutlinedTextField(
                value = memo,
                onValueChange = {
                    memo = it
                    settings.confessionMemo = it
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                minLines = 3,
                placeholder = { Text("체크리스트 외에 고백할 내용을 적어 두세요") },
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                "체크한 항목과 메모는 이 휴대폰(로컬)에만 저장되며 백업·동기화에 포함되지 않아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (pickDate) {
        DatePickDialog(
            initial = lastDate ?: today,
            onDismiss = { pickDate = false },
            onPick = { picked ->
                if (picked.isAfter(today)) toast("오늘 이후의 날짜는 기록할 수 없어요")
                else saveDate(picked)
            },
        )
    }

    if (confirmRecordToday) {
        AlertDialog(
            onDismissRequest = { confirmRecordToday = false },
            title = { Text("오늘 성사일을 기록할까요?") },
            text = { Text("성사를 마치셨다면 양심성찰 체크와 메모도 함께 비울 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRecordToday = false
                    saveDate(today, clearPrep = true)
                }) { Text("기록하고 비우기") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmRecordToday = false }) { Text("취소") }
                    TextButton(onClick = {
                        confirmRecordToday = false
                        saveDate(today, clearPrep = false)
                    }) { Text("기록만 하기") }
                }
            },
        )
    }

    if (confirmClearDate) {
        AlertDialog(
            onDismissRequest = { confirmClearDate = false },
            title = { Text("성사일 기록을 지울까요?") },
            text = { Text("저장된 마지막 고해성사 날짜를 지워요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearDate = false
                    lastEpochDay = null
                    settings.lastConfessionEpochDay = null
                    toast("성사일 기록을 지웠어요")
                }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClearDate = false }) { Text("취소") } },
        )
    }

    readingPrayer?.let { p ->
        AlertDialog(
            onDismissRequest = { readingPrayer = null },
            title = { Text(p.title) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(p.text.trimIndent(), style = ScriptureStyle)
                }
            },
            confirmButton = { TextButton(onClick = { readingPrayer = null }) { Text("닫기") } },
        )
    }
}
