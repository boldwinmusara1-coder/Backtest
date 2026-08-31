package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// TradingView Pro Theme Palette
val TvBackground = Color(0xFF131722) // Classic TradingView deep obsidian canvas
val TvSurface = Color(0xFF1E222D)    // TradingView toolbar & card surface
val TvSurfaceElevated = Color(0xFF2A2E39) // TradingView modal & elevated component background
val TvBorder = Color(0xFF2A2E39)     // TradingView sleek border line
val TvBorderSubtle = Color(0xFF363A45)

// TradingView Iconic Brand Accents
val TvBlue = Color(0xFF2962FF)       // TradingView iconic electric royal blue
val TvBlueContainer = Color(0xFF1A337E) // Blue container background
val TvBlueText = Color(0xFFD6E4FF)   // Blue tint label text
val TvBlueGlow = Color(0x332962FF)

// TradingView Price Action Colors (Exact Chart Values)
val TvGreen = Color(0xFF089981)      // Authentic TradingView pine/emerald bull candle
val TvGreenGlow = Color(0x22089981)
val TvGreenDark = Color(0xFF056B5B)

val TvRed = Color(0xFFF23645)        // Authentic TradingView crimson bear candle
val TvRedGlow = Color(0x22F23645)
val TvRedDark = Color(0xFFA82530)

val TvAmber = Color(0xFFFF9800)      // TradingView warning & benchmark orange
val TvCyan = Color(0xFF00BCD4)       // TradingView secondary indicator cyan
val TvPurple = Color(0xFF7E57C2)     // TradingView RSI & indicator purple

// TradingView Typography Text Colors
val TvTextPrimary = Color(0xFFD1D4DC)   // Crisp TradingView off-white text
val TvTextSecondary = Color(0xFF787B86) // TradingView slate secondary text
val TvTextMuted = Color(0xFF50535E)     // TradingView subtle muted text

// Chart Specific Colors
val ChartGridLine = Color(0x1A787B86)
val BenchmarkLine = TvAmber
val EquityLine = TvGreen
val FastMaLine = TvBlue
val SlowMaLine = TvAmber
val BollingerUpperLine = Color(0xAA00BCD4)
val BollingerLowerLine = Color(0xAA00BCD4)
val SupertrendLine = TvGreen

// Aliases for seamless compatibility across entire codebase
val BentoBackground = TvBackground
val BentoCardBg = TvSurface
val BentoCardElevated = TvSurfaceElevated
val BentoBorder = TvBorder

val BentoLilac = TvBlue
val BentoLilacContainer = TvBlueContainer
val BentoLilacText = TvBlueText
val BentoLilacGlow = TvBlueGlow

val BentoGreen = TvGreen
val BentoGreenContainer = Color(0xFF0D3E35)
val BentoGreenText = Color(0xFFA3EAD8)
val BentoGreenGlow = TvGreenGlow
val BentoRed = TvRed
val BentoRedGlow = TvRedGlow
val BentoAmber = TvAmber
val BentoCyan = TvCyan

val BentoTextPrimary = TvTextPrimary
val BentoTextSecondary = TvTextSecondary
val BentoTextMuted = TvTextMuted

val TradeDarkBg = TvBackground
val TradeSurfaceDark = TvSurface
val TradeSurfaceElevated = TvSurfaceElevated
val TradeSurfaceCard = TvSurface
val TradeBorder = TvBorder

val BullGreen = TvGreen
val BullGreenGlow = TvGreenGlow
val BullGreenDark = TvGreenDark

val BearRed = TvRed
val BearRedGlow = TvRedGlow
val BearRedDark = TvRedDark

val CyanAccent = TvBlue
val CyanGlow = TvBlueGlow

val AmberGold = TvAmber
val PurpleAccent = TvPurple

val TextPrimary = TvTextPrimary
val TextSecondary = TvTextSecondary
val TextMuted = TvTextMuted
