package com.hdlee73.dailyhabit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.RESTAURANT_CATEGORIES
import com.hdlee73.dailyhabit.data.RESTAURANT_PRICES
import com.hdlee73.dailyhabit.data.RESTAURANT_TAGS
import com.hdlee73.dailyhabit.data.Restaurant
import com.hdlee73.dailyhabit.data.RestaurantVisit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private enum class PlaceFilter(val label: String) { ALL("전체"), VISITED("가본 곳"), WISH("가보고 싶은 곳"), REVISIT("또 갈 곳") }
private enum class PlaceSort(val label: String) { RECENT("최근 추가"), RATING("별점 높은 순"), VISITS("많이 간 순"), NAME("이름 순") }

private val StarColor = Color(0xFFE5A50A)

@Composable
fun RestaurantScreen(openEditor: Boolean = false, onEditorOpened: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).restaurantDao() }
    val all by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(PlaceFilter.ALL) }
    var category by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PlaceSort.RECENT) }
    var viewing by remember { mutableStateOf<Restaurant?>(null) }
    var editing by remember { mutableStateOf<Restaurant?>(null) }
    var creating by remember { mutableStateOf(false) }
    val visitDao = remember { AppDatabase.get(context).visitDao() }
    val visits by visitDao.observeAll().collectAsState(initial = emptyList())
    var mode by remember { mutableIntStateOf(0) }
    // 첫 탭이 아니면 뒤로 가기로 첫 탭으로 돌아간다
    ScreenBackHandler(enabled = mode != 0) { mode = 0 }
    var calMonth by remember { mutableStateOf(YearMonth.now()) }
    var calDay by remember { mutableStateOf(LocalDate.now()) }
    val placeById = remember(all) { all.associateBy { it.id } }

    // 방문 기록이 바뀌면 맛집의 방문 횟수·최근 방문일을 맞춘다
    suspend fun syncVisits(restaurantId: Long) {
        val vs = visitDao.of(restaurantId)
        val r = dao.byId(restaurantId) ?: return
        dao.update(
            r.copy(
                visitCount = vs.size,
                lastVisitEpochDay = vs.maxOfOrNull { it.epochDay },
                wish = if (vs.isNotEmpty()) false else r.wish,
            )
        )
    }

    LaunchedEffect(openEditor) {
        if (openEditor) {
            creating = true
            onEditorOpened()
        }
    }

    val q = query.trim()
    val list = all
        .filter {
            when (filter) {
                PlaceFilter.ALL -> true
                PlaceFilter.VISITED -> !it.wish
                PlaceFilter.WISH -> it.wish
                PlaceFilter.REVISIT -> it.revisit
            }
        }
        .filter { category == null || it.category == category }
        .filter {
            q.isEmpty() || listOf(it.name, it.menu, it.address, it.comment, it.memo, it.tags, it.category)
                .any { f -> f.contains(q, ignoreCase = true) }
        }
        .let { l ->
            when (sort) {
                PlaceSort.RECENT -> l.sortedByDescending { it.createdAt }
                PlaceSort.RATING -> l.sortedWith(compareByDescending<Restaurant> { it.rating }.thenByDescending { it.visitCount })
                PlaceSort.VISITS -> l.sortedByDescending { it.visitCount }
                PlaceSort.NAME -> l.sortedBy { it.name }
            }
        }
    // 현재 보고 있는 항목은 DB 변경을 따라가도록
    val viewingLive = viewing?.let { v -> all.firstOrNull { it.id == v.id } }

    Box(modifier.fillMaxSize()) {
        TopLazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SegmentedTabs(listOf("목록", "방문 달력", "지도"), mode, { mode = it })
            }
            if (mode == 2) {
                mapItems(all, context) { viewing = it }
            } else if (mode == 1) {
                item(key = "calendar") {
                    VisitCalendar(calMonth, visits, calDay, onMonth = { calMonth = it }, onSelect = { calDay = it })
                }
                val dayVisits = visits.filter { it.epochDay == calDay.toEpochDay() }
                item(key = "calendar-day") {
                    Text(
                        "${formatKoreanDate(calDay)} · " + if (dayVisits.isEmpty()) "방문한 맛집이 없어요" else "${dayVisits.size}곳 방문",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
                items(dayVisits, key = { "v${it.id}" }) { v ->
                    val place = placeById[v.restaurantId]
                    if (place != null) SoftCard(onClick = { viewing = place }) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        val meta = listOf(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (v.note.isNotBlank()) Text(
                            "“${v.note}”",
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (v.photo.isNotBlank()) PhotoThumb(v.photo, 120.dp, Modifier.padding(top = 10.dp))
                    }
                }
            } else {
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("식당, 메뉴, 동네로 찾기") },
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaceFilter.entries.forEach { f ->
                            val n = when (f) {
                                PlaceFilter.ALL -> all.size
                                PlaceFilter.VISITED -> all.count { !it.wish }
                                PlaceFilter.WISH -> all.count { it.wish }
                                PlaceFilter.REVISIT -> all.count { it.revisit }
                            }
                            FilterChip(filter == f, { filter = f }, { Text(if (n > 0) "${f.label} $n" else f.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf<String?>(null) + RESTAURANT_CATEGORIES).forEach { c ->
                            FilterChip(category == c, { category = c }, { Text(c ?: "모든 종류") })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("정렬", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PlaceSort.entries.forEach { s ->
                            TextButton(onClick = { sort = s }) {
                                Text(
                                    s.label,
                                    color = if (sort == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    if (all.isEmpty()) EmptyState("🍽️", "나만의 맛집 리스트를 만들어 보세요", "다녀온 곳은 별점과 한 줄 평을, 가보고 싶은 곳은 위치와 링크를 저장해 두세요.")
                    else EmptyState("🔍", "조건에 맞는 맛집이 없어요", "검색어나 필터를 바꿔 보세요.")
                }
            }
            items(list, key = { it.id }) { r -> RestaurantCard(r) { viewing = r } }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("맛집 추가") },
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(50),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (viewingLive != null) {
        RestaurantDetailSheet(
            r = viewingLive,
            onDismiss = { viewing = null },
            onEdit = { editing = viewingLive; viewing = null },
            visits = visits.filter { it.restaurantId == viewingLive.id },
            onAddVisit = { d, note, photo ->
                scope.launch {
                    visitDao.insert(RestaurantVisit(restaurantId = viewingLive.id, epochDay = d.toEpochDay(), note = note, photo = photo))
                    syncVisits(viewingLive.id)
                }
            },
            onUpdateVisit = { v -> scope.launch { visitDao.update(v); syncVisits(v.restaurantId) } },
            onDeleteVisit = { id ->
                scope.launch {
                    visits.firstOrNull { it.id == id }?.let { com.hdlee73.dailyhabit.data.Photos.delete(context, it.photo) }
                    visitDao.delete(id)
                    syncVisits(viewingLive.id)
                }
            },
            onToggleRevisit = { scope.launch { dao.update(viewingLive.copy(revisit = !viewingLive.revisit)) } },
            onRate = { stars -> scope.launch { dao.update(viewingLive.copy(rating = stars)) } },
        )
    }
    if (creating || editing != null) {
        RestaurantEditorSheet(
            initial = editing ?: Restaurant(name = "", wish = filter == PlaceFilter.WISH, category = category.orEmpty()),
            isNew = editing == null,
            onDismiss = { creating = false; editing = null },
            onSave = { r ->
                scope.launch { if (r.id == 0L) dao.insert(r) else dao.update(r) }
                creating = false; editing = null
            },
            onDelete = { r ->
                scope.launch {
                    visits.filter { it.restaurantId == r.id }.forEach { com.hdlee73.dailyhabit.data.Photos.delete(context, it.photo) }
                    visitDao.deleteOf(r.id)
                    dao.delete(r)
                }
                editing = null
            },
        )
    }
}

@Composable
fun StarRow(rating: Int, size: Dp = 16.dp, onRate: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..5) {
            Icon(
                if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (onRate != null) "$i 점" else null,
                tint = if (i <= rating) StarColor else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(size)
                    .then(if (onRate != null) Modifier.clickable { onRate(if (rating == i) 0 else i) } else Modifier),
            )
        }
    }
}

private fun priceLabel(p: Int) = if (p in 1 until RESTAURANT_PRICES.size) RESTAURANT_PRICES[p] else ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestaurantCard(r: Restaurant, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOf(r.category, r.address, priceLabel(r.price)).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            when {
                r.wish -> Pill("가보고 싶어요", MaterialTheme.colorScheme.tertiary)
                r.revisit -> Pill("또 갈래요", MaterialTheme.colorScheme.primary)
            }
        }
        if (!r.wish && r.rating > 0) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StarRow(r.rating, 15.dp)
            if (r.visitCount > 0) Text("  ${r.visitCount}번 방문", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (r.comment.isNotBlank()) {
            Text(
                "“${r.comment}”",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val chips = r.menu.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(4)
        if (chips.isNotEmpty() || r.tagList.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { Pill("🍴 $it", MaterialTheme.colorScheme.secondary) }
                r.tagList.forEach { Pill("#$it", MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RestaurantDetailSheet(
    r: Restaurant,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    visits: List<RestaurantVisit>,
    onAddVisit: (LocalDate, String, String) -> Unit,
    onUpdateVisit: (RestaurantVisit) -> Unit,
    onDeleteVisit: (Long) -> Unit,
    onToggleRevisit: () -> Unit,
    onRate: (Int) -> Unit,
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var visitAdding by remember { mutableStateOf(false) }
    var visitEditing by remember { mutableStateOf<RestaurantVisit?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(listOf(r.category, priceLabel(r.price)).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "맛집" },
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(r.name, style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StarRow(r.rating, 26.dp, onRate)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            r.visitCount > 0 -> "${r.visitCount}번 방문" + (r.lastVisitEpochDay?.let { " · 최근 ${formatKoreanDate(LocalDate.ofEpochDay(it))}" } ?: "")
                            r.wish -> "아직 안 가봤어요"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (r.comment.isNotBlank()) item {
                Text("“${r.comment}”", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.titleMedium)
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (r.menu.isNotBlank()) InfoLine(Icons.Filled.RestaurantMenu, "주요 메뉴", r.menu)
                    if (r.address.isNotBlank()) InfoLine(Icons.Filled.Place, "위치", r.address)
                    if (r.memo.isNotBlank()) InfoLine(Icons.AutoMirrored.Filled.Notes, "메모", r.memo)
                    if (r.tagList.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.tagList.forEach { Pill("#$it", MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (r.menu.isBlank() && r.address.isBlank() && r.memo.isBlank() && r.tagList.isEmpty()) {
                        Text("메뉴, 위치, 메모를 추가해 보세요.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val q = r.address.ifBlank { r.name }
                            val uri = Uri.parse("geo:0,0?q=" + Uri.encode(if (r.address.isBlank()) q else "${r.name} ${r.address}"))
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://map.naver.com/p/search/" + Uri.encode(q))))
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Filled.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("지도") }
                    if (r.link.isNotBlank()) FilledTonalButton(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.link.trim()))) } },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Filled.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("링크") }
                    FilledTonalButton(
                        onClick = {
                            val text = buildString {
                                append("🍽️ ").append(r.name)
                                if (r.rating > 0) append(" ").append("★".repeat(r.rating))
                                if (r.comment.isNotBlank()) append("\n“").append(r.comment).append("”")
                                if (r.menu.isNotBlank()) append("\n추천 메뉴: ").append(r.menu)
                                if (r.address.isNotBlank()) append("\n위치: ").append(r.address)
                                if (r.link.isNotBlank()) append("\n").append(r.link)
                            }
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "맛집 공유"))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("공유") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "방문 기록" + if (visits.isNotEmpty()) " ${visits.size}" else "",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { visitAdding = true }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("방문 기록하기")
                    }
                }
                if (visits.isEmpty()) Text(
                    "다녀온 날을 기록해 두면 ‘방문 달력’에서 볼 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                visits.forEach { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { visitEditing = v }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (v.photo.isNotBlank()) PhotoThumb(v.photo, 44.dp, Modifier.padding(end = 12.dp))
                        Text(
                            LocalDate.ofEpochDay(v.epochDay).let { "${it.year}년 ${formatKoreanDate(it)}" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            v.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        Icon(Icons.Filled.Edit, "수정", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("또 갈래요", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = r.revisit, onCheckedChange = { onToggleRevisit() })
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                    ) { Text("수정") }
                }
            }
        }
    }
    if (visitAdding) {
        VisitDialog(null, onDismiss = { visitAdding = false }, onDelete = null, onSave = { d, n, p -> onAddVisit(d, n, p); visitAdding = false })
    }
    visitEditing?.let { v ->
        VisitDialog(
            v,
            onDismiss = { visitEditing = null },
            onDelete = { onDeleteVisit(v.id); visitEditing = null },
            onSave = { d, n, p -> onUpdateVisit(v.copy(epochDay = d.toEpochDay(), note = n, photo = p)); visitEditing = null },
        )
    }
}

@Composable
private fun VisitDialog(
    initial: RestaurantVisit?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = initial?.photo.orEmpty()
    var photo by remember { mutableStateOf(original) }
    // 이 창에서 새로 가져온 사진 (저장하지 않고 닫으면 지운다)
    val created = remember { mutableListOf<String>() }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            val name = com.hdlee73.dailyhabit.data.Photos.import(context, uri)
            if (name == null) android.widget.Toast.makeText(context, "사진을 가져오지 못했어요", android.widget.Toast.LENGTH_SHORT).show()
            else { created.add(name); photo = name }
        }
    }
    fun discardNew(keep: String) {
        created.filter { it != keep }.forEach { com.hdlee73.dailyhabit.data.Photos.delete(context, it) }
    }
    val dismiss = { discardNew(original); onDismiss() }
    var date by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.epochDay) } ?: LocalDate.now()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (initial == null) "방문 기록하기" else "방문 기록 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${date.year}년 ${formatKoreanDate(date)}")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 80) note = it },
                    label = { Text("그날의 한 줄 메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                if (photo.isNotBlank()) {
                    PhotoThumb(photo, 160.dp)
                    Row {
                        TextButton(onClick = {
                            pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("사진 바꾸기") }
                        TextButton(onClick = { photo = "" }) { Text("사진 빼기") }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("📷 사진 추가") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                discardNew(photo)
                if (original.isNotBlank() && original != photo) com.hdlee73.dailyhabit.data.Photos.delete(context, original)
                onSave(date, note.trim(), photo)
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = dismiss) { Text("취소") }
            }
        },
    )
    if (pick) DatePickDialog(date, onDismiss = { pick = false }) { date = it; pick = false }
}

@Composable
private fun VisitCalendar(
    month: YearMonth,
    visits: List<RestaurantVisit>,
    selected: LocalDate,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val counts = remember(visits) { visits.groupingBy { it.epochDay }.eachCount() }
    val inMonth = visits.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달") }
            Text(
                "${month.year}년 ${month.monthValue}월",
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
        }
        Text(
            if (inMonth.isEmpty()) "이 달에 기록한 방문이 없어요" else "이 달 ${inMonth.map { it.restaurantId }.distinct().size}곳 · ${inMonth.size}번 방문",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val offset = month.atDay(1).dayOfWeek.value % 7
        val rows = (offset + month.lengthOfMonth() + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum in 1..month.lengthOfMonth()) {
                        val d = month.atDay(dayNum)
                        CalendarDayCell(d, counts[d.toEpochDay()] ?: 0, d == selected, d == today) { onSelect(d) }
                    } else {
                        Box(Modifier.weight(1f).height(50.dp))
                    }
                }
            }
        }
        TextButton(
            onClick = { onMonth(YearMonth.from(today)); onSelect(today) },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("오늘로") }
    }
}

@Composable
private fun RowScope.CalendarDayCell(day: LocalDate, count: Int, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .weight(1f)
            .height(50.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) primary.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (isToday) Modifier.border(1.5.dp, primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodyMedium)
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(if (count > 0) 7.dp else 0.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(primary),
        )
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RestaurantEditorSheet(
    initial: Restaurant,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Restaurant) -> Unit,
    onDelete: (Restaurant) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial.name) }
    var category by remember { mutableStateOf(initial.category) }
    var wish by remember { mutableStateOf(initial.wish) }
    var rating by remember { mutableIntStateOf(initial.rating) }
    var menu by remember { mutableStateOf(initial.menu) }
    var address by remember { mutableStateOf(initial.address) }
    var link by remember { mutableStateOf(initial.link) }
    var comment by remember { mutableStateOf(initial.comment) }
    var memo by remember { mutableStateOf(initial.memo) }
    var tags by remember { mutableStateOf(initial.tagList.toSet()) }
    var price by remember { mutableIntStateOf(initial.price) }
    var revisit by remember { mutableStateOf(initial.revisit) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text(if (isNew) "맛집 추가" else "맛집 수정", style = MaterialTheme.typography.headlineSmall) }
            item {
                OutlinedTextField(name, { name = it }, label = { Text("식당 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                SegmentedTabs(listOf("가봤어요", "가보고 싶어요"), if (wish) 1 else 0, { wish = it == 1 })
            }
            if (!wish) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("별점", Modifier.width(56.dp), style = MaterialTheme.typography.bodyLarge)
                    StarRow(rating, 32.dp) { rating = it }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("종류")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RESTAURANT_CATEGORIES.forEach { c ->
                            FilterChip(category == c, { category = if (category == c) "" else c }, { Text(c) })
                        }
                    }
                }
            }
            item {
                OutlinedTextField(menu, { menu = it }, label = { Text("주요 메뉴") }, placeholder = { Text("예: 평양냉면, 수육 (쉼표로 구분)") },
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(address, { address = it }, label = { Text("위치") }, placeholder = { Text("주소나 동네, 예: 서울 중구 을지로") },
                    leadingIcon = { Icon(Icons.Filled.Place, null) }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(link, { link = it }, label = { Text("지도·블로그 링크 (선택)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Link, null) }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(comment, { comment = it }, label = { Text("한 줄 평") },
                    placeholder = { Text(if (wish) "예: 친구가 강력 추천한 곳" else "예: 육수가 깔끔하고 면이 쫄깃함") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(memo, { memo = it }, label = { Text("메모") },
                    placeholder = { Text("웨이팅, 주차, 다음에 먹어볼 메뉴, 같이 간 사람 등") },
                    minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("이럴 때 좋아요")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RESTAURANT_TAGS.forEach { t ->
                            FilterChip(t in tags, { tags = if (t in tags) tags - t else tags + t }, { Text(t) })
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("가격대 (1인 기준)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RESTAURANT_PRICES.forEachIndexed { i, label ->
                            FilterChip(price == i, { price = i }, { Text(label) })
                        }
                    }
                }
            }
            if (!wish) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("또 갈래요", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = revisit, onCheckedChange = { revisit = it })
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isNew) OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("삭제") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
                        onClick = {
                            val becameVisited = initial.wish && !wish
                            onSave(
                                initial.copy(
                                    name = name.trim(), category = category, wish = wish,
                                    rating = if (wish) 0 else rating, menu = menu.trim(), address = address.trim(),
                                    link = link.trim(), comment = comment.trim(), memo = memo.trim(),
                                    tags = RESTAURANT_TAGS.filter { it in tags }.joinToString(","), price = price,
                                    revisit = !wish && revisit,
                                    visitCount = if ((isNew && !wish) || becameVisited) maxOf(1, initial.visitCount) else initial.visitCount,
                                    lastVisitEpochDay = if ((isNew && !wish) || becameVisited) LocalDate.now().toEpochDay() else initial.lastVisitEpochDay,
                                )
                            )
                        },
                    ) { Text("저장") }
                }
            }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("맛집 삭제") },
            text = { Text("‘${initial.name}’을(를) 목록에서 지울까요?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

/** 동네(주소 앞부분)로 묶은 목록. 앱 안에 지도를 넣으려면 지도 서비스 키가 필요해서, 지도 앱을 여는 방식으로 만들었다 */
private fun areaOf(address: String): String {
    val words = address.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "위치를 적지 않은 곳"
        words.size == 1 -> words[0]
        else -> words[0] + " " + words[1]
    }
}

private fun openMap(context: android.content.Context, query: String) {
    val geo = Uri.parse("geo:0,0?q=" + Uri.encode(query))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://map.naver.com/p/search/" + Uri.encode(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mapItems(
    all: List<Restaurant>,
    context: android.content.Context,
    onOpen: (Restaurant) -> Unit,
) {
    val groups = all.groupBy { areaOf(it.address) }.entries.sortedWith(
        compareBy<Map.Entry<String, List<Restaurant>>> { it.key.startsWith("위치를") }.thenByDescending { it.value.size }
    )
    item(key = "map-info") {
        SoftCard(color = MaterialTheme.colorScheme.surfaceContainer, bordered = false) {
            Text("📍 저장한 ${all.size}곳을 동네별로 모았어요", style = MaterialTheme.typography.titleSmall)
            Text(
                "동네 이름 옆 버튼을 누르면 지도 앱에서 그 동네의 맛집을 찾아 보여줘요. 맛집 줄의 핀을 누르면 그 식당 위치가 열려요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (all.isEmpty()) item(key = "map-empty") {
        EmptyState("🗺️", "저장한 맛집이 없어요", "맛집을 추가하면 동네별로 모아 볼 수 있어요.")
    }
    groups.forEach { (area, list) ->
        item(key = "map-h-$area") {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$area · ${list.size}곳", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!area.startsWith("위치를")) TextButton(onClick = { openMap(context, "$area 맛집") }) {
                    Icon(Icons.Filled.Map, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("지도 앱")
                }
            }
        }
        items(list, key = { "map-${it.id}" }) { r ->
            SoftCard(onClick = { onOpen(r) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val meta = listOf(r.category, r.address).filter { it.isNotBlank() }.joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { openMap(context, if (r.address.isBlank()) r.name else "${r.name} ${r.address}") }) {
                        Icon(Icons.Filled.Place, "지도에서 보기", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
