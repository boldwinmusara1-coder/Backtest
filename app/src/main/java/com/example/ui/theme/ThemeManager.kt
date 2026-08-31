package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String, val description: String) {
    SYSTEM("System Default", "Follows Android OS theme"),
    DARK("Dark Mode", "Deep obsidian canvas for high contrast"),
    LIGHT("Light Mode", "Crisp high-readability daylight canvas")
}

data class AppThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val brandPrimary: Color,
    val brandPrimaryContainer: Color,
    val brandPrimaryText: Color,
    val tradeGreen: Color,
    val tradeGreenContainer: Color,
    val tradeGreenText: Color,
    val tradeRed: Color,
    val tradeRedContainer: Color,
    val tradeRedText: Color,
    val tradeAmber: Color,
    val tradeCyan: Color,
    val tradePurple: Color,
    val accentGreen: Color = tradeGreen,
    val accentRed: Color = tradeRed,
    val brandSecondary: Color = tradeCyan
)

typealias AppColors = AppThemeColors

val DarkThemeColors = AppThemeColors(
    isDark = true,
    background = Color(0xFF0E1118),
    surface = Color(0xFF181C26),
    surfaceElevated = Color(0xFF222736),
    border = Color(0xFF2B3142),
    borderSubtle = Color(0xFF1F2432),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    brandPrimary = Color(0xFF3B82F6),
    brandPrimaryContainer = Color(0xFF1E3A8A),
    brandPrimaryText = Color(0xFFBFDBFE),
    tradeGreen = Color(0xFF089981),
    tradeGreenContainer = Color(0xFF064E3B),
    tradeGreenText = Color(0xFFA7F3D0),
    tradeRed = Color(0xFFF23645),
    tradeRedContainer = Color(0xFF7F1D1D),
    tradeRedText = Color(0xFFFECACA),
    tradeAmber = Color(0xFFF59E0B),
    tradeCyan = Color(0xFF06B6D4),
    tradePurple = Color(0xFF8B5CF6)
)

val LightThemeColors = AppThemeColors(
    isDark = false,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F5F9),
    border = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFEDF2F7),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    brandPrimary = Color(0xFF2563EB),
    brandPrimaryContainer = Color(0xFFDBEAFE),
    brandPrimaryText = Color(0xFF1E40AF),
    tradeGreen = Color(0xFF059669),
    tradeGreenContainer = Color(0xFFD1FAE5),
    tradeGreenText = Color(0xFF065F46),
    tradeRed = Color(0xFFDC2626),
    tradeRedContainer = Color(0xFFFEE2E2),
    tradeRedText = Color(0xFF991B1B),
    tradeAmber = Color(0xFFD97706),
    tradeCyan = Color(0xFF0891B2),
    tradePurple = Color(0xFF7C3AED)
)

val LocalAppTheme = staticCompositionLocalOf { DarkThemeColors }

object ThemeManager {
    private const val PREFS_NAME = "tradestrat_theme_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    private var sharedPreferences: SharedPreferences? = null
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedName = sharedPreferences?.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
            val initial = try {
                AppThemeMode.valueOf(savedName ?: AppThemeMode.SYSTEM.name)
            } catch (e: Exception) {
                AppThemeMode.SYSTEM
            }
            _themeMode.value = initial
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        sharedPreferences?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }
}
