# A+ TRENDLINE TRADING SYSTEM — SPECIFICATION & GOVERNANCE DOCUMENT

**Document Version:** 1.0.0-SPEC-LOCK  
**Strategy Identifier:** `A+ Trendline System` (`APlusTrendlineStrategy`)  
**Engine Profile Name:** `A_PLUS_ENGINE_DEFAULT`  
**Governance Status:** PRE-BACKTEST SPECIFICATION LOCK (Performance Backtest Gate: **BLOCKED**)  

---

## 1. PURPOSE & ARCHITECTURAL SEPARATION

This document establishes the formal boundary between:
1. **USER-SPECIFIED TRADING RULES**: Fundamental market logic, core price-action principles, and qualitative requirements explicitly defined by the trader.
2. **ENGINE IMPLEMENTATION ASSUMPTIONS**: Discrete numerical parameters, lookback constants, calculation tolerances, and algorithmic approximations introduced by the software engine to make qualitative rules computable.

> **CRITICAL GOVERNANCE MANDATE:**  
> Engine implementation parameters are **unconfirmed baseline assumptions**. They are **NOT** user-specified trading rules and must **NEVER** be optimized, swept, or curve-fitted prior to explicit human trader review and approval.

---

## 2. SYSTEM RULE CLASSIFICATION MATRIX

| # | Rule / Mechanism | Plain Language Description | Exact Deterministic Implementation | Classification | Status / Value |
|---|---|---|---|---|---|
| **R01** | **Primary Strategy Domain** | Strategy trades trendlines constructed across market highs/lows. | System detects multi-touch linear trendlines on confirmed pivot points. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R02** | **Market Structure Framework** | Market structure defines directional bias (Bullish = HH/HL, Bearish = LH/LL). | Swings classified as HH/HL/LH/LL based on prior confirmed structural points. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R03** | **Trendline Anchor Requirement** | Trendlines require at least 2 meaningful structural touches. | Minimum confirmed touch count threshold `minTouches >= 2`. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R04** | **Setup Preference** | Break setups are preferred over bounce setups. | Engine default setup mode set to `BREAK_AND_RETEST`. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R05** | **Primary Setup Archetype** | Break-and-retest is the primary trade setup. | System waits for a breakout bar close, registers a pending retest, and seeks retest rejection. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R06** | **Entry Confirmation** | Entries require price rejection/confirmation rather than immediate intrabar wick piercing. | Retest confirmed on candle close respecting line zone with directional close or wick rejection. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R07** | **Structural Stop Loss** | Initial stop loss must be anchored to valid market structure rather than arbitrary dollar or pip amounts. | Long stop placed below last confirmed swing low; Short stop placed above last confirmed swing high. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R08** | **Unbounded Profit Development** | Winning trades must be allowed to trend rather than prematurely capping at fixed 2R. | Fixed Take Profit is unbounded (`null`); trade runs dynamically with trailing stops. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R09** | **Structural Break-Even** | Risk elimination must be tied to favorable structural progress. | Stop moved to entry price once favorable excursion reaches threshold ($+1.0\text{R}$). | **USER-SPECIFIED** | Confirmed Core Rule |
| **R10** | **Structural Trailing** | Trailing stop must follow confirmed market structure pivots. | Long stop monotonically ratcheted under confirmed higher lows; Short stop above lower highs. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R11** | **Counter-Structure Exit** | Counter-trend market structure transition triggers early protective exit. | Position exits immediately upon a confirmed counter-structure break against trade direction. | **USER-SPECIFIED** | Confirmed Core Rule |
| **R12** | **No Look-Ahead Invariant** | Decisions must use strictly causal data available at the historical moment. | Swing at bar $p$ invisible until bar $p + confirmationBars$; no future leakage permitted. | **USER-SPECIFIED** | Confirmed Invariant |
| **R13** | **Swing Pivot Lookback** | Number of left bars required to qualify a local high or low pivot. | `swingLookback = 5` bars. Candidate $H_p \ge H_k$ for $k \in [p-5, p+3]$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R14** | **Swing Confirmation Delay** | Number of right bars required to causally confirm a swing pivot. | `confirmationBars = 3` bars. Swing at $p$ becomes usable at bar $p + 3$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R15** | **Swing Separation Distance** | Minimum bar distance between two consecutive pivots of the same type. | `minSwingSeparationBars = 4` bars. Prevents tightly clustered minor wicks. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R16** | **Trendline Lifespan Limit** | Maximum age of a trendline before it becomes stale and pruned. | `maxLineAgeBars = 150` bars from origin anchor $x_1$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R17** | **Breakout Confirmation Pct** | Percentage penetration beyond line required to confirm true break. | `breakConfirmationPct = 0.15%`. Close must exceed $LinePrice \times (1 \pm 0.0015)$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R18** | **Retest Tolerance Band** | Price zone around broken trendline where a pullback counts as a retest tap. | `retestTolerancePct = 0.35%`. Retest zone $[LinePrice \times (1 - 0.0070), LinePrice \times (1 + 0.0035)]$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R19** | **Retest Expiration Window** | Maximum bars allowed after breakout for retest to occur before setup expires. | `retestMaxBars = 12` bars. If $currentBar - breakBar > 12$, retest expires. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R20** | **Structural Stop Buffer** | Safety buffer placed beyond the structural swing point for stop loss. | `stopBufferPct = 0.10%`. Stop = $SwingLow \times (1 - 0.0010)$ or $SwingHigh \times (1 + 0.0010)$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R21** | **Break-Even Trigger Level** | Favorable excursion magnitude required before moving stop loss to breakeven. | `breakEvenTriggerR = 1.0R`. Stop moved to entry price when MFE $\ge +1.0\text{R}$. | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |
| **R22** | **Structural Trailing Trigger** | Precise mechanic for trailing ratchet activation. | Monotonic ratchet under each new confirmed swing pivot ($p + 3$). | **IMPLEMENTATION ASSUMPTION** | **Requires User Confirmation** |

---

## 3. DETAILED RULE BREAKDOWN

### A. USER-SPECIFIED TRADING RULES (12 RULES)

1. **Trendline Trading as Core Domain (R01):** The strategy operates by identifying directional trendlines (descending resistance lines across swing highs; ascending support lines across swing lows).
2. **Market Structure Framework (R02):** Market structure governs setup validation. Bullish structure consists of higher highs (HH) and higher lows (HL). Bearish structure consists of lower highs (LH) and lower lows (LL).
3. **Multi-Touch Requirement (R03):** Trendlines require at least two distinct structural swing points ($x_1, y_1$) and ($x_2, y_2$). Single-point tangents are strictly invalid.
4. **Break Preference (R04):** The trading strategy prioritizes trendline breaks over trendline bounces.
5. **Break-and-Retest Setup Archetype (R05):** The primary setup requires:
   - A confirmed close breaking through the active trendline.
   - A subsequent pullback (retest) into the broken trendline area.
   - Rejection out of the retest zone confirming support/resistance flip.
6. **Confirmation on Close / Candle Rejection (R06):** Orders are never placed purely on intrabar wick touches. Entries require closed candle confirmation exhibiting price rejection (directional body or rejection wick).
7. **Structural Stop Loss (R07):** Stop loss must reflect market structure — placed below the last confirmed swing low for long positions, or above the last confirmed swing high for short positions.
8. **Uncapped Upside / Runner Development (R08):** Eliminates arbitrary fixed take-profit targets (such as fixed 2R limits) so that high-quality trending moves can fully unfold.
9. **Structural Break-Even (R09):** Risk is eliminated by ratcheting the stop loss to breakeven once a meaningful structural expansion occurs.
10. **Structural Trailing Stop (R10):** The stop loss is actively trailed behind new confirmed structural swing levels as the trend progresses.
11. **Counter-Structure Early Exit (R11):** If the market creates a confirmed counter-trend structure break against the position, the trade is terminated immediately to preserve capital.
12. **Strict Causal Real-Time Invariant (R12):** No indicator or swing detection may use future bars. Swings must have an explicit confirmation lag before becoming actionable.

---

### B. ENGINE IMPLEMENTATION ASSUMPTIONS (10 PARAMETERS / ASSUMPTIONS)

The following 10 values are **unconfirmed algorithmic baselines** implemented to execute the logic in code. They require explicit review and confirmation from the human trader:

1. **`swingLookback = 5`**: Left lookback window for identifying local high/low candidate bars.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
2. **`confirmationBars = 3`**: Right confirmation window before a swing is marked confirmed and visible.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
3. **`minSwingSeparationBars = 4`**: Minimum temporal spacing between consecutive swing points of the same type.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
4. **`maxLineAgeBars = 150`**: Maximum chronological age (in bars) before a trendline is pruned.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
5. **`breakConfirmationPct = 0.15%`**: Minimum percentage price clearance beyond the trendline for a breakout close.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
6. **`retestTolerancePct = 0.35%`**: Symmetric tolerance band around the trendline defining the retest zone.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
7. **`retestMaxBars = 12`**: Maximum bar count after breakout before a pending retest setup is cancelled.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
8. **`stopBufferPct = 0.10%`**: Additional percentage cushion applied beyond the structural swing anchor for stop placement.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
9. **`breakEvenTriggerR = 1.0R`**: Specific favorable excursion multiple required to trigger moving stop to breakeven.  
   *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`
10. **`trailingTrigger = CONFIRMED_SWING`**: Stop loss trails strictly when a new swing completes $p + 3$ confirmation.  
    *Status:* `IMPLEMENTATION ASSUMPTION — REQUIRES USER CONFIRMATION`

---

## 4. PROFILE NAMING & ENGINE FREEZE

To prevent conflation between human-approved rules and software baseline assumptions, the profile is officially designated:

```
Profile Name: A_PLUS_ENGINE_DEFAULT
Status: BASELINE ENGINE ASSUMPTIONS (PENDING HUMAN TRADER CONFIRMATION)
```

The profile will only be promoted to `A_PLUS_HUMAN_APPROVED` once the 10 implementation assumptions have been reviewed and accepted or adjusted by the human trader.

---

## 5. PERFORMANCE BACKTEST GATE STATUS

| Gate Item | Status |
|---|---|
| User-Specified Rules Documented | **LOCKED & DOCUMENTED** |
| Implementation Assumptions Explicitly Identified | **LOCKED & IDENTIFIED** |
| Strategy Version Recorded | **`1.0.0-frozen`** |
| Parameters Frozen (No Sweeps / No Optimization) | **FROZEN** |
| No-Look-Ahead Causal Tests | **PASS (100.0%)** |
| Forensic Audit Log Tests | **PASS (100.0%)** |
| Human Rule Specification Lock | **LOCKED** |
| **Performance Backtest Execution Gate** | **BLOCKED (Awaiting User Review)** |

---
