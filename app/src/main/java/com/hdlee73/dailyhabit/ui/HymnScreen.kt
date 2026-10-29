package com.hdlee73.dailyhabit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hdlee73.dailyhabit.data.Hymn
import com.hdlee73.dailyhabit.data.HymnDetail
import com.hdlee73.dailyhabit.data.HymnRepository

@Composable
fun HymnScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { HymnRepository(context) }
    var openNo by rememberSaveable { mutableStateOf<Int?>(null) }
    val open = openNo?.let { repo.byNumber(it) }
    if (open != null) {
        ScreenBackHandler { openNo = null }
        HymnDetailScreen(repo, open, onBack = { openNo = null }, onGo = { openNo = it }, modifier = modifier)
    } else {
        HymnList(repo, onOpen = { openNo = it.number }, modifier = modifier)
    }
}

private const val ALL = "전체"

@Composable
private fun HymnList(repo: HymnRepository, onOpen: (Hymn) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf(ALL) }
    val list = remember(query, season) {
        HymnRepository.search(repo.all, query).filter { season == ALL || it.season == season }
    }
    TopLazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("번호 또는 제목으로 찾기") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "지우기") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (repo.seasons.isNotEmpty()) item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (listOf(ALL) + repo.seasons).forEach { s -> FilterChip(season == s, { season = s }, { Text(s) }) }
            }
        }
        item {
            Text(
                "${list.size}곡",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        if (list.isEmpty()) item {
            Text(
                "찾는 성가가 없어요. 번호나 제목의 다른 낱말로 찾아보세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(list, key = { it.number }) { h ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .clickable { onOpen(h) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${h.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
                Text(h.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (h.season.isNotBlank()) Pill(h.season, MaterialTheme.colorScheme.secondary, Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun HymnDetailScreen(
    repo: HymnRepository,
    h: Hymn,
    onBack: () -> Unit,
    onGo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var detail by remember(h.number) { mutableStateOf<HymnDetail?>(null) }
    var error by remember(h.number) { mutableStateOf<String?>(null) }
    var reload by remember(h.number) { mutableIntStateOf(0) }
    var mono by rememberSaveable { mutableStateOf(true) }
    var score by remember(h.number) { mutableStateOf<ImageBitmap?>(null) }
    var scoreLoading by remember(h.number) { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }

    LaunchedEffect(h.number, reload) {
        error = null
        repo.detail(h.number, forceRefresh = reload > 0)
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "알 수 없는 오류" }
    }
    LaunchedEffect(detail, mono) {
        val d = detail ?: return@LaunchedEffect
        val url = if (mono) d.monoUrl.ifBlank { d.mixedUrl } else d.mixedUrl.ifBlank { d.monoUrl }
        scoreLoading = true
        score = repo.score(h.number, url, mono = url == d.monoUrl)?.asImageBitmap()
        scoreLoading = false
    }

    val prev = repo.all.lastOrNull { it.number < h.number }
    val next = repo.all.firstOrNull { it.number > h.number }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "목록으로") }
            Text("성가 목록", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { prev?.let { onGo(it.number) } }, enabled = prev != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 성가")
            }
            IconButton(onClick = { next?.let { onGo(it.number) } }, enabled = next != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 성가")
            }
        }
        TopScrollColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 32.dp),
        ) {
            SectionLabel(listOf("가톨릭 성가 ${h.number}번", h.season).filter { it.isNotBlank() }.joinToString(" · "),
                color = MaterialTheme.colorScheme.secondary)
            Text(h.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

            val d = detail
            when {
                d == null && error == null -> Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) { CircularProgressIndicator() }
                d == null -> Column {
                    Text("악보를 불러오지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요.", style = MaterialTheme.typography.bodyMedium)
                    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { reload++ }) { Text("다시 시도") }
                }
                else -> {
                    if (d.monoUrl.isNotBlank() && d.mixedUrl.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(mono, { mono = true }, { Text("단성 악보") })
                            FilterChip(!mono, { mono = false }, { Text("혼성 악보") })
                            Spacer(Modifier.weight(1f))
                            if (score != null) IconButton(onClick = { zoom = true }) { Icon(Icons.Filled.ZoomIn, "크게 보기") }
                        }
                    }
                    // 악보 (가사가 함께 적혀 있다). 누르면 크게 보기
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .clickable(enabled = score != null) { zoom = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = score
                        when {
                            bmp != null -> Image(bmp, contentDescription = "${h.number}번 ${h.title} 악보",
                                contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                            scoreLoading -> CircularProgressIndicator(Modifier.padding(40.dp))
                            else -> Text("악보 이미지를 불러오지 못했어요", style = MaterialTheme.typography.bodySmall,
                                color = Color.DarkGray, modifier = Modifier.padding(32.dp))
                        }
                    }
                    if (d.lyrics.isNotBlank()) {
                        SectionLabel("가사", Modifier.padding(top = 22.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.secondary)
                        d.lyrics.lines().forEach { line ->
                            Text(line, style = ScriptureStyle, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "가사와 악보: 굿뉴스 가톨릭 성가 (한국천주교주교회의 「가톨릭 성가」). 한 번 연 곡은 인터넷 없이도 볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.pageUrl(h.number)))) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("굿뉴스에서 보기 · 듣기")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }

    val bmp = score
    if (zoom && bmp != null) ScoreZoomDialog(bmp, onDismiss = { zoom = false })
}

/** 악보를 화면 가득 보여 주고 두 손가락으로 확대 · 이동, 두 번 눌러 확대/원래대로 */
@Composable
private fun ScoreZoomDialog(bmp: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, z, _ ->
                        scale = (scale * z).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bmp,
                contentDescription = "악보 크게 보기",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, "닫기", tint = Color.DarkGray)
            }
        }
    }
}
