package com.hdlee73.dailyhabit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** 왼쪽 메뉴가 열려 있는지. 열려 있으면 뒤로 가기는 메뉴를 닫는 데 쓴다. */
val LocalMenuOpen = compositionLocalOf { false }

/** 화면 안의 뒤로 가기(상세 → 목록, 다른 탭 → 첫 탭). 메뉴가 열려 있을 때는 끼어들지 않는다. */
@Composable
fun ScreenBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    BackHandler(enabled = enabled && !LocalMenuOpen.current, onBack = onBack)
}
