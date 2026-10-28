package com.hdlee73.dailyhabit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.BuildConfig
import com.hdlee73.dailyhabit.data.AppSettings
import com.hdlee73.dailyhabit.data.UpdateInfo
import com.hdlee73.dailyhabit.data.Updater
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Ready(val info: UpdateInfo, val file: File, val hint: String?) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(settings.autoUpdateCheck) }
    var lastChecked by remember { mutableStateOf(settings.lastUpdateCheck) }
    var state by remember {
        mutableStateOf<UpdateUi>(Updater.available?.takeIf { Updater.hasUpdate }?.let { UpdateUi.Available(it) } ?: UpdateUi.Idle)
    }

    fun check() {
        state = UpdateUi.Checking
        scope.launch {
            runCatching { Updater.fetchLatest() }
                .onSuccess { latest ->
                    settings.lastUpdateCheck = System.currentTimeMillis()
                    lastChecked = settings.lastUpdateCheck
                    Updater.available = latest
                    state = if (latest != null && Updater.isNewer(latest.version, BuildConfig.VERSION_NAME)) UpdateUi.Available(latest)
                    else UpdateUi.UpToDate
                }
                .onFailure { state = UpdateUi.Failed("확인하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.") }
        }
    }

    fun download(info: UpdateInfo) {
        state = UpdateUi.Downloading(info, 0f)
        scope.launch {
            runCatching { Updater.download(context, info) { p -> state = UpdateUi.Downloading(info, p) } }
                .onSuccess { file ->
                    val opened = Updater.install(context, file)
                    state = UpdateUi.Ready(
                        info, file,
                        if (opened) null else "설정에서 ‘이 출처의 앱 설치’를 허용한 뒤 돌아와 아래 버튼을 다시 눌러 주세요.",
                    )
                }
                .onFailure { state = UpdateUi.Failed("내려받지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.") }
        }
    }

    TopScrollColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 앱 이름과 버전
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFE08A68), Color(0xFFC25E3E)))),
                contentAlignment = Alignment.Center,
            ) { Text("🌅", fontSize = 40.sp) }
            Text(
                "DailyHabit",
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                "버전 ${BuildConfig.VERSION_NAME}  (빌드 ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Pill(
                if (BuildConfig.CATHOLIC) "천주교인용" else "일반용",
                MaterialTheme.colorScheme.primary,
                Modifier.padding(top = 8.dp),
            )
        }

        // 업데이트
        SoftCard {
            SectionLabel("업데이트", color = MaterialTheme.colorScheme.primary)
            when (val s = state) {
                UpdateUi.Idle, UpdateUi.UpToDate, is UpdateUi.Failed -> {
                    Text(
                        when (s) {
                            UpdateUi.UpToDate -> "최신 버전을 쓰고 있어요 ✓"
                            is UpdateUi.Failed -> s.message
                            else -> "새 버전이 나왔는지 확인해 보세요"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (lastChecked > 0) Text(
                        "마지막 확인 ${SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN).format(Date(lastChecked))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Button(onClick = ::check, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("업데이트 확인") }
                }
                UpdateUi.Checking -> {
                    Text("확인하는 중…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 14.dp))
                }
                is UpdateUi.Available -> {
                    Text("새 버전 v${s.info.version}이 있어요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        "현재 v${BuildConfig.VERSION_NAME}" + if (s.info.apkSize > 0) " · 내려받기 ${"%.1f".format(s.info.apkSize / 1048576.0)}MB" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.info.notes.isNotBlank()) Text(
                        s.info.notes.lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim().trimStart('#', ' ') }.take(500),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Button(onClick = { download(s.info) }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("다운로드하고 설치") }
                    Text(
                        "설치해도 할 일, 일기 같은 기록은 그대로 남아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                is UpdateUi.Downloading -> {
                    Text("v${s.info.version} 내려받는 중… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                }
                is UpdateUi.Ready -> {
                    Text("v${s.info.version} 설치 준비가 됐어요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        s.hint ?: "설치 화면에서 ‘업데이트’를 눌러 주세요. 설치가 끝나면 앱이 다시 시작돼요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = {
                            val opened = Updater.install(context, s.file)
                            state = s.copy(hint = if (opened) null else s.hint)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    ) { Text("설치 화면 열기") }
                    OutlinedButton(onClick = ::check, modifier = Modifier.fillMaxWidth()) { Text("다시 확인") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("새 버전 알림 받기", style = MaterialTheme.typography.bodyLarge)
                    Text("새 버전이 나오면 알림을 보내고, 설치할 때까지 앱을 열 때마다 알려줘요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = autoCheck, onCheckedChange = { autoCheck = it; settings.autoUpdateCheck = it })
            }
        }

        // 사용 방법
        SoftCard {
            SectionLabel("사용 방법", color = MaterialTheme.colorScheme.primary)
            HOW_TO.forEach { (title, body) ->
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 만든이
        SoftCard {
            SectionLabel("만든이", color = MaterialTheme.colorScheme.primary)
            Text("이현덕 with Claude", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            InfoRow(Icons.Outlined.Email, "이메일", "hdlee73@gmail.com") { openUrl(context, "mailto:hdlee73@gmail.com") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Outlined.Home, "홈페이지", "hdlee73.github.io/Home") { openUrl(context, "https://hdlee73.github.io/Home/") }
        }

        SoftCard {
            InfoRow(Icons.Outlined.History, "변경 내역 · 이전 버전", "GitHub 릴리스") { openUrl(context, Updater.RELEASES_PAGE) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Outlined.Code, "소스 코드", "github.com/${Updater.REPO}") { openUrl(context, "https://github.com/${Updater.REPO}") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Outlined.RateReview, "의견 · 오류 알리기", "GitHub 이슈") { openUrl(context, "https://github.com/${Updater.REPO}/issues") }
        }

        SoftCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "할 일, 루틴, 일기, 장보기, 독서, 맛집 기록은 이 휴대폰에만 저장돼요. " +
                        "폰을 바꾸기 전에는 설정의 ‘백업 파일 만들기’로 옮겨 두세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
            Text(
                (if (BuildConfig.CATHOLIC) "말씀(독서 · 화답송 · 복음): 한국천주교주교회의 매일미사 · 기도문: 가톨릭 기도서\n가톨릭 성가: 굿뉴스 가톨릭 성가\n" else "") +
                    "일정: 휴대폰에 동기화된 구글 캘린더",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** 앱 정보의 '사용 방법' (판본별) */
private val HOW_TO: List<Pair<String, String>>
    get() = buildList {
        add("메뉴" to "왼쪽 위 ☰ 버튼이나 화면 왼쪽 끝에서 밀어 메뉴를 열고 원하는 화면을 고르세요. 뒤로 가기를 누르면 직전 화면으로 돌아가요.")
        if (BuildConfig.CATHOLIC) {
            add("오늘의 말씀" to "그날 미사의 제1독서 · 화답송 · 제2독서(주일·대축일) · 복음 환호송 · 복음을 순서대로 보여 줘요. 위쪽 버튼으로 한 부분만 골라 볼 수 있고, 한 번 받은 말씀은 인터넷 없이도 보여요.")
            add("기도" to "주요 기도문, 오늘의 묵주기도 신비, 성월 기도(3·5·6·9·10·11월)를 볼 수 있어요. 성월에는 위쪽에 ‘이달의 성월’이 나와요.")
            add("가톨릭 성가" to "번호나 제목으로 찾고, 곡을 누르면 가사가 적힌 악보가 나와요. 단성/혼성 악보를 바꿔 보고, 악보를 누르면 두 손가락으로 크게 볼 수 있어요.")
        } else {
            add("오늘의 명언" to "날마다 바뀌는 명언을 한국어와 영어로 보여 줘요.")
        }
        add("하루 관리" to "일정(구글 캘린더) · 할 일 · 루틴 · 한 줄 일기 · 맛집 · 독서 · 월간 돌아보기. 메뉴 아래쪽 + 버튼으로 바로 추가할 수 있어요.")
        add("백업 · 동기화" to "설정에서 백업 파일을 만들거나 복원하고, 같은 와이파이의 PC와 동기화할 수 있어요.")
        add("업데이트" to "새 버전이 나오면 알림이 오고, 이 화면에서 바로 내려받아 설치할 수 있어요.")
    }

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
    }
}
