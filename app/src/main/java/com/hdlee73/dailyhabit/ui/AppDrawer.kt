package com.hdlee73.dailyhabit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdlee73.dailyhabit.BuildConfig

fun Section.icon(): ImageVector = when (this) {
    Section.GOSPEL -> Icons.AutoMirrored.Outlined.MenuBook
    Section.PRAYER -> Icons.Outlined.VolunteerActivism
    Section.SCHEDULE -> Icons.Outlined.CalendarMonth
    Section.TODO -> Icons.Outlined.CheckCircle
    Section.ROUTINE -> Icons.Outlined.Repeat
    Section.SETTINGS -> Icons.Outlined.Settings
    Section.QUOTE -> Icons.Outlined.FormatQuote
    Section.RESTAURANT -> Icons.Outlined.RestaurantMenu
    Section.JOURNAL -> Icons.Outlined.EditNote
    Section.SHOPPING -> Icons.Outlined.ShoppingCart
    Section.BOOKS -> Icons.Outlined.AutoStories
    Section.ABOUT -> Icons.Outlined.Info
    Section.REVIEW -> Icons.Outlined.Insights
    Section.HYMN -> Icons.Outlined.MusicNote
    Section.CONFESSION -> Icons.AutoMirrored.Outlined.FactCheck
}

/** 시안 A '클리어': 왼쪽에서 열리는 반투명 흰 메뉴, 묶음 제목 + 파란 선택 표시 */
@Composable
fun AppDrawer(
    current: Section,
    badges: Map<Section, String>,
    onSelect: (Section) -> Unit,
    onCreate: (Section) -> Unit,
) {
    var newMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(colors.surfaceContainerLowest.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        // 머리: 앱 로고 + 이름 + 판본·버전, 오른쪽 설정
        Row(Modifier.padding(start = 6.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.WbTwilight, null, tint = colors.onPrimary, modifier = Modifier.size(24.dp)) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("DailyHabit", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(
                    (if (BuildConfig.CATHOLIC) "천주교인용" else "일반") + " · v" + BuildConfig.VERSION_NAME,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onSelect(Section.SETTINGS) }) {
                Icon(Icons.Outlined.Settings, "설정", tint = if (current == Section.SETTINGS) colors.primary else colors.onSurfaceVariant)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GroupCaption(if (BuildConfig.CATHOLIC) "신앙" else "오늘")
            Section.primary.forEach { DrawerItem(it, current == it, badges[it], onSelect) }
            GroupCaption("하루 관리")
            Section.daily.forEach { DrawerItem(it, current == it, badges[it], onSelect) }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = colors.outlineVariant)
            DrawerItem(Section.ABOUT, current == Section.ABOUT, badges[Section.ABOUT], onSelect)
        }

        // 아래: 새로 만들기 (파란 알약 버튼)
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primary)
                    .clickable { newMenu = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                Text("새로 만들기", color = colors.onPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
            DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                listOf(
                    Section.TODO to "할 일", Section.ROUTINE to "루틴", Section.JOURNAL to "한 줄 일기",
                    Section.RESTAURANT to "맛집", Section.BOOKS to "책",
                ).forEach { (s, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(s.icon(), null, tint = colors.primary) },
                        onClick = { newMenu = false; onCreate(s) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun DrawerItem(section: Section, selected: Boolean, badge: String?, onSelect: (Section) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fg = if (selected) colors.onPrimary else colors.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable { onSelect(section) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon(), null, tint = if (selected) colors.onPrimary else colors.onSurfaceVariant, modifier = Modifier.size(21.dp))
        Text(
            section.label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        )
        if (!badge.isNullOrEmpty()) {
            Text(badge, color = if (selected) colors.onPrimary.copy(alpha = 0.85f) else colors.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}
