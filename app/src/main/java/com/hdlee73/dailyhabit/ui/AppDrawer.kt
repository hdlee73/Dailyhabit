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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
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
}

private val AvatarColor = Color(0xFF7B6CF0)

/** Claude 앱 사이드바를 닮은 왼쪽 메뉴 */
@Composable
fun AppDrawer(
    current: Section,
    badges: Map<Section, String>,
    onSelect: (Section) -> Unit,
    onCreate: (Section) -> Unit,
) {
    var newMenu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxHeight()
            .width(304.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 22.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DailyHabit",
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSelect(Section.SETTINGS) }) {
                Icon(Icons.Outlined.Settings, "설정", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Section.primary.forEach { DrawerItem(it, current == it, badges[it], onSelect) }
            HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "하루 관리",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            )
            Section.daily.forEach { DrawerItem(it, current == it, badges[it], onSelect) }
            HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DrawerItem(Section.ABOUT, current == Section.ABOUT, badges[Section.ABOUT], onSelect)
        }

        // 아래: 아바타 + 검은 알약 버튼
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(AvatarColor).clickable { onSelect(Section.SETTINGS) },
                contentAlignment = Alignment.Center,
            ) { Text(if (BuildConfig.CATHOLIC) "✝" else "D", color = Color.White, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f))
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface)
                        .clickable { newMenu = true }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
                    Text("새로 만들기", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp))
                }
                DropdownMenu(expanded = newMenu, onDismissRequest = { newMenu = false }) {
                    listOf(
                        Section.TODO to "할 일", Section.ROUTINE to "루틴", Section.JOURNAL to "한 줄 일기",
                        Section.RESTAURANT to "맛집", Section.BOOKS to "책",
                    ).forEach { (s, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(s.icon(), null) },
                            onClick = { newMenu = false; onCreate(s) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(section: Section, selected: Boolean, badge: String?, onSelect: (Section) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .clickable { onSelect(section) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon(), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Text(
            section.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        if (!badge.isNullOrEmpty()) Text(badge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
