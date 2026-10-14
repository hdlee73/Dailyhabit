package com.hdlee73.dailyhabit.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hdlee73.dailyhabit.BuildConfig
import com.hdlee73.dailyhabit.data.PhoneSync
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 설정의 'PC와 동기화' 카드 (휴대폰 쪽) */
@Composable
fun SyncCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by PhoneSync.status(context).collectAsState()
    val s = status ?: return
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }

    fun pairWith(text: String) {
        busy = true
        scope.launch {
            val err = PhoneSync.pair(context, text)
            busy = false
            if (err == null) Toast.makeText(context, "PC와 짝을 맺고 동기화했어요", Toast.LENGTH_SHORT).show() else error = err
        }
    }

    val scanner = androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let { pairWith(it) }
    }

    SoftCard {
        SectionLabel("PC와 동기화", color = MaterialTheme.colorScheme.primary)
        Text(
            "같은 와이파이에 있는 PC의 DailyHabit${if (BuildConfig.CATHOLIC) " 천주교인용" else ""}(윈도우 v1.8.0 이상)과 할 일, 루틴, 일기, 맛집(사진 포함), 독서, 장보기 기록을 서로 맞춰요. " +
                "인터넷 서버나 계정 없이 직접 주고받고, 짝을 맺을 때 나눈 비밀 키로 암호화해요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (s.paired) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Computer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(s.pcName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            s.running -> "동기화하는 중…"
                            s.lastSync > 0 -> "마지막 동기화 " + SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(s.lastSync)) +
                                (if (s.ok && s.message.isNotBlank()) " · ${s.message}" else "")
                            else -> "아직 동기화 전"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!s.ok && s.message.isNotBlank() && !s.running) {
                Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            FilledTonalButton(
                onClick = { scope.launch { PhoneSync.syncNow(context) } },
                enabled = !s.running,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) { Text(if (s.running) "동기화하는 중…" else "지금 동기화") }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                TextButton(onClick = { scanner.launch(scanOptions()) }, enabled = !busy) { Text("다른 PC와 짝 맺기") }
                TextButton(onClick = { confirmUnpair = true }) { Text("연결 해제", color = MaterialTheme.colorScheme.error) }
            }
            Text(
                "앱을 열 때와 닫을 때, 그리고 와이파이에 있는 동안 30분마다 자동으로 맞춰요. 같은 기록을 양쪽에서 고쳤으면 나중에 고친 쪽이 남아요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "PC 앱의 설정 → 휴대폰과 동기화 → ‘휴대폰과 짝 맺기’를 누르면 QR 코드가 떠요. 휴대폰과 PC가 같은 와이파이에 있어야 해요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            FilledTonalButton(
                onClick = { scanner.launch(scanOptions()) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) { Text(if (busy) "짝 맺는 중…" else "QR 코드로 짝 맺기") }
            OutlinedButton(onClick = { typing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("짝 맺기 코드 붙여 넣기") }
        }
    }

    if (typing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("짝 맺기 코드") },
            text = {
                Column {
                    Text("PC의 QR 창에서 ‘짝 맺기 코드 복사’를 눌러 휴대폰으로 보낸 뒤 붙여 넣으세요. DH1;로 시작해요.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = false, minLines = 3)
                }
            },
            confirmButton = { TextButton(onClick = { typing = false; pairWith(text) }, enabled = text.isNotBlank()) { Text("짝 맺기") } },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("취소") } },
        )
    }
    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("PC 연결을 해제할까요?") },
            text = { Text("이 휴대폰과 PC의 기록은 그대로 남고, 더 이상 서로 맞추지 않아요. 다시 쓰려면 QR 코드로 짝을 맺으면 돼요.") },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; PhoneSync.unpair(context) }) { Text("해제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("취소") } },
        )
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("짝을 맺지 못했어요") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("확인") } },
        )
    }
}

private fun scanOptions() = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt("PC 화면의 QR 코드를 비춰 주세요")
    .setBeepEnabled(false)
    .setOrientationLocked(false)
