package com.hdlee73.dailyhabit.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hdlee73.dailyhabit.data.AppDatabase
import com.hdlee73.dailyhabit.data.Book
import com.hdlee73.dailyhabit.data.BookNote
import com.hdlee73.dailyhabit.data.BookStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun shortDate(epochDay: Long?): String =
    epochDay?.let { LocalDate.ofEpochDay(it).let { d -> "${d.year}.${d.monthValue}.${d.dayOfMonth}" } }.orEmpty()

@Composable
fun BooksScreen(openEditor: Boolean = false, onEditorOpened: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).bookDao() }
    val books by dao.observeBooks().collectAsState(initial = emptyList())
    val notes by dao.observeNotes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    // 첫 탭이 아니면 뒤로 가기로 첫 탭으로 돌아간다
    ScreenBackHandler(enabled = tab != 0) { tab = 0 }
    var viewingId by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    val settings = remember { com.hdlee73.dailyhabit.data.AppSettings(context) }
    val thisYear = LocalDate.now().year
    var goal by remember { mutableIntStateOf(settings.readingGoal(thisYear)) }
    var editGoal by remember { mutableStateOf(false) }

    LaunchedEffect(openEditor) {
        if (openEditor) {
            creating = true
            onEditorOpened()
        }
    }
    // 읽는 중인 책이 없으면 처음엔 '읽고 싶은' 탭을 보여준다
    LaunchedEffect(books.isEmpty()) {
        if (books.isNotEmpty() && books.none { it.status == BookStatus.READING.ordinal } && tab == 0) tab = 1
    }

    val status = BookStatus.entries[tab]
    val list = books.filter { it.status == status.ordinal }.let { l ->
        if (status == BookStatus.DONE) l.sortedByDescending { it.endEpochDay ?: it.createdAt } else l
    }
    val doneThisYear = books.filter {
        it.status == BookStatus.DONE.ordinal && it.endEpochDay?.let { d -> LocalDate.ofEpochDay(d).year } == thisYear
    }
    val viewing = viewingId?.let { id -> books.firstOrNull { it.id == id } }

    Box(modifier.fillMaxSize()) {
        TopLazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ReadingGoalCard(goal, doneThisYear.size, thisYear) { editGoal = true }
            }
            item {
                SegmentedTabs(
                    BookStatus.entries.map { s -> books.count { it.status == s.ordinal }.let { n -> if (n > 0) "${s.label} $n" else s.label } },
                    tab,
                    { tab = it },
                )
            }
            if (status == BookStatus.DONE && doneThisYear.isNotEmpty()) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBubbleSimple("${thisYear}년 읽은 책", "${doneThisYear.size}권", Modifier.weight(1f))
                    StatBubbleSimple("읽은 쪽수", "${doneThisYear.sumOf { it.totalPages }}쪽", Modifier.weight(1f))
                }
            }
            if (list.isEmpty()) item {
                when (status) {
                    BookStatus.READING -> EmptyState("📚", "읽고 있는 책이 없어요", "‘읽고 싶은’ 책에서 시작하거나 새 책을 추가해 보세요.")
                    BookStatus.WANT -> EmptyState("🔖", "읽고 싶은 책을 모아 보세요", "책 제목만 적어 두어도 좋아요.")
                    BookStatus.DONE -> EmptyState("🏁", "아직 다 읽은 책이 없어요", "다 읽으면 별점과 한 줄 소감을 남길 수 있어요.")
                }
            }
            items(list, key = { it.id }) { b ->
                BookCard(b, notes.count { it.bookId == b.id }) { viewingId = b.id }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("책 추가") },
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(50),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    if (editGoal) {
        var text by remember { mutableStateOf(if (goal > 0) goal.toString() else "") }
        AlertDialog(
            onDismissRequest = { editGoal = false },
            title = { Text("${thisYear}년 독서 목표") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("올해 읽을 책 (권)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    goal = text.toIntOrNull() ?: 0
                    settings.setReadingGoal(thisYear, goal)
                    editGoal = false
                }) { Text("저장") }
            },
            dismissButton = {
                Row {
                    if (goal > 0) TextButton(onClick = {
                        goal = 0
                        settings.setReadingGoal(thisYear, 0)
                        editGoal = false
                    }) { Text("목표 없애기", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { editGoal = false }) { Text("취소") }
                }
            },
        )
    }

    if (viewing != null) {
        BookDetailSheet(
            book = viewing,
            notes = notes.filter { it.bookId == viewing.id },
            onDismiss = { viewingId = null },
            onUpdate = { scope.launch { dao.update(it) } },
            onAddNote = { text, page -> scope.launch { dao.insertNote(BookNote(bookId = viewing.id, text = text, page = page)) } },
            onDeleteNote = { scope.launch { dao.deleteNote(it) } },
            onDelete = { scope.launch { dao.delete(viewing.id) }; viewingId = null },
        )
    }
    if (creating) {
        BookEditDialog(
            initial = Book(title = "", status = if (tab == 0) BookStatus.READING.ordinal else tab),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { b ->
                val today = LocalDate.now().toEpochDay()
                val fixed = when (b.statusEnum) {
                    BookStatus.READING -> b.copy(startEpochDay = today)
                    BookStatus.DONE -> b.copy(startEpochDay = today, endEpochDay = today, currentPage = b.totalPages)
                    BookStatus.WANT -> b
                }
                scope.launch { dao.insert(fixed) }
                tab = fixed.status
                creating = false
            },
        )
    }
}

@Composable
private fun ReadingGoalCard(goal: Int, done: Int, year: Int, onEdit: () -> Unit) {
    SoftCard(onClick = onEdit, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) {
        SectionLabel("${year}년 독서 목표", color = MaterialTheme.colorScheme.primary)
        if (goal <= 0) {
            Text("올해 읽을 책 권수를 정해 보세요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text("지금까지 ${done}권 읽었어요 · 눌러서 목표 정하기", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        } else {
            val today = LocalDate.now()
            val expected = goal.toFloat() * today.dayOfYear / today.lengthOfYear()
            val remaining = (goal - done).coerceAtLeast(0)
            val monthsLeft = 12 - today.monthValue + 1
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text("$done", style = MaterialTheme.typography.headlineMedium)
                Text(" / ${goal}권", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 3.dp))
            }
            LinearProgressIndicator(
                progress = { (done.toFloat() / goal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            Text(
                when {
                    remaining == 0 -> "목표 달성! 🎉 정말 대단해요"
                    done >= expected + 0.5f -> "계획보다 앞서 가고 있어요 · 남은 ${remaining}권"
                    done + 0.5f < expected -> "조금 늦었어요 · 남은 ${remaining}권, 한 달에 약 ${"%.1f".format(remaining.toFloat() / monthsLeft)}권"
                    else -> "계획대로 잘 가고 있어요 · 남은 ${remaining}권"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatBubbleSimple(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier) {
        SoftCard(color = MaterialTheme.colorScheme.surfaceContainer, bordered = false) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun BookCard(b: Book, noteCount: Int, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(b.title, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotBlank()) Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (noteCount > 0) Pill("밑줄 $noteCount", MaterialTheme.colorScheme.secondary)
        }
        when (b.statusEnum) {
            BookStatus.READING -> {
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    if (b.totalPages > 0) "${b.currentPage}/${b.totalPages}쪽 · ${(b.progress * 100).toInt()}%" else "${b.currentPage}쪽까지 읽었어요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            BookStatus.DONE -> {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (b.rating > 0) StarRow(b.rating, 15.dp)
                    val range = listOf(shortDate(b.startEpochDay), shortDate(b.endEpochDay)).filter { it.isNotEmpty() }.joinToString(" ~ ")
                    if (range.isNotEmpty()) Text(
                        (if (b.rating > 0) "  " else "") + range,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (b.review.isNotBlank()) Text(
                    "“${b.review}”",
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BookStatus.WANT -> if (b.totalPages > 0) Text(
                "${b.totalPages}쪽",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookDetailSheet(
    book: Book,
    notes: List<BookNote>,
    onDismiss: () -> Unit,
    onUpdate: (Book) -> Unit,
    onAddNote: (String, Int) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pageText by remember(book.id) { mutableStateOf(book.currentPage.toString()) }
    var noteText by remember(book.id) { mutableStateOf("") }
    var notePage by remember(book.id) { mutableStateOf("") }
    var review by remember(book.id) { mutableStateOf(book.review) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()

    fun setStatus(s: BookStatus) {
        if (s.ordinal == book.status) return
        onUpdate(
            when (s) {
                BookStatus.WANT -> book.copy(status = s.ordinal)
                BookStatus.READING -> book.copy(status = s.ordinal, startEpochDay = book.startEpochDay ?: today, endEpochDay = null)
                BookStatus.DONE -> book.copy(
                    status = s.ordinal,
                    startEpochDay = book.startEpochDay ?: today,
                    endEpochDay = today,
                    currentPage = if (book.totalPages > 0) book.totalPages else book.currentPage,
                )
            }
        )
        if (s == BookStatus.DONE && book.totalPages > 0) pageText = book.totalPages.toString()
    }

    fun setPage(p: Int) {
        val page = if (book.totalPages > 0) p.coerceIn(0, book.totalPages) else p.coerceAtLeast(0)
        pageText = page.toString()
        val finished = book.totalPages > 0 && page >= book.totalPages
        onUpdate(
            when {
                finished -> book.copy(currentPage = page, status = BookStatus.DONE.ordinal,
                    startEpochDay = book.startEpochDay ?: today, endEpochDay = today)
                book.status == BookStatus.WANT.ordinal && page > 0 -> book.copy(currentPage = page,
                    status = BookStatus.READING.ordinal, startEpochDay = book.startEpochDay ?: today)
                else -> book.copy(currentPage = page)
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(book.title, style = MaterialTheme.typography.headlineMedium)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SegmentedTabs(BookStatus.entries.map { it.label }, book.status, { setStatus(BookStatus.entries[it]) })
            }

            if (book.statusEnum != BookStatus.DONE) item {
                SoftCard {
                    SectionLabel("읽은 쪽수")
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pageText,
                            onValueChange = { v -> v.filter { it.isDigit() }.take(5).let { pageText = it; setPage(it.toIntOrNull() ?: 0) } },
                            label = { Text(if (book.totalPages > 0) "현재 쪽 / ${book.totalPages}" else "현재 쪽") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(150.dp),
                        )
                        FilledTonalButton(onClick = { setPage(book.currentPage + 10) }, modifier = Modifier.padding(start = 10.dp)) { Text("+10") }
                        FilledTonalButton(onClick = { setPage(book.currentPage + 30) }, modifier = Modifier.padding(start = 6.dp)) { Text("+30") }
                    }
                    if (book.startEpochDay != null) Text(
                        "${shortDate(book.startEpochDay)} 시작",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            if (book.statusEnum == BookStatus.DONE) item {
                SoftCard {
                    SectionLabel("다 읽었어요")
                    Text(
                        listOf(shortDate(book.startEpochDay), shortDate(book.endEpochDay)).filter { it.isNotEmpty() }.joinToString(" ~ "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Box(Modifier.padding(top = 10.dp)) { StarRow(book.rating, 28.dp) { onUpdate(book.copy(rating = it)) } }
                    OutlinedTextField(
                        value = review,
                        onValueChange = { if (it.length <= 120) review = it },
                        label = { Text("한 줄 소감") },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        maxLines = 3,
                    )
                    if (review != book.review) TextButton(onClick = { onUpdate(book.copy(review = review.trim())) }) { Text("소감 저장") }
                }
            }

            item {
                SoftCard {
                    SectionLabel("밑줄 · 메모 ${if (notes.isEmpty()) "" else notes.size}")
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("인상 깊은 문장이나 떠오른 생각") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = notePage,
                            onValueChange = { notePage = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("쪽") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(90.dp),
                        )
                        Box(Modifier.weight(1f))
                        FilledTonalButton(
                            enabled = noteText.isNotBlank(),
                            onClick = { onAddNote(noteText.trim(), notePage.toIntOrNull() ?: 0); noteText = ""; notePage = "" },
                        ) { Text("추가") }
                    }
                }
            }
            items(notes, key = { "n${it.id}" }) { n ->
                SoftCard(color = MaterialTheme.colorScheme.surfaceContainer, bordered = false) {
                    Text(
                        n.text,
                        fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOf(if (n.page > 0) "p.${n.page}" else "", shortDate(java.time.Instant.ofEpochMilli(n.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()))
                                .filter { it.isNotEmpty() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            val quote = "“${n.text}”\n— ${book.title}${if (book.author.isNotBlank()) ", ${book.author}" else ""}${if (n.page > 0) " p.${n.page}" else ""}"
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, quote), "문장 공유")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Icon(Icons.Filled.Share, "공유") }
                        IconButton(onClick = { onDeleteNote(n.id) }) { Icon(Icons.Filled.Close, "삭제") }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("책 정보 수정") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (editing) {
        BookEditDialog(book, isNew = false, onDismiss = { editing = false }, onSave = { onUpdate(it); editing = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 책을 지울까요?") },
            text = { Text("밑줄과 메모도 함께 지워져요.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookEditDialog(initial: Book, isNew: Boolean, onDismiss: () -> Unit, onSave: (Book) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var author by remember { mutableStateOf(initial.author) }
    var pages by remember { mutableStateOf(if (initial.totalPages > 0) initial.totalPages.toString() else "") }
    var status by remember { mutableIntStateOf(initial.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "책 추가" else "책 정보") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("제목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("지은이") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pages, { pages = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("전체 쪽수 (모르면 비워 두세요)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (isNew) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookStatus.entries.forEach { s -> FilterChip(status == s.ordinal, { status = s.ordinal }, { Text(s.label) }) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val total = pages.toIntOrNull() ?: 0
                    onSave(
                        initial.copy(
                            title = title.trim(), author = author.trim(), totalPages = total, status = status,
                            currentPage = if (total > 0) initial.currentPage.coerceAtMost(total) else initial.currentPage,
                        )
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
