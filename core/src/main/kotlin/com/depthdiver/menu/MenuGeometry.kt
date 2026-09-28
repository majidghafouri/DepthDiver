package com.depthdiver.menu

import com.depthdiver.Profile
import com.depthdiver.Widgets
import com.depthdiver.common.Strings
import com.depthdiver.game.SubScreenLayout
import com.depthdiver.game.SubScreenLayoutFactory
import com.depthdiver.game.UiScale
import kotlin.math.max

/** A box in menu/screen space, shared by the drawing and the hit test. */
data class MenuRect(val cx: Float, val cy: Float, val w: Float, val h: Float)

/** Row order on the settings screen. */
enum class Setting {
    MUSIC, SFX, MASTER, REDUCE_MOTION, HIGH_CONTRAST, SCREEN_SHAKE;

    val isSlider: Boolean get() = this == MUSIC || this == SFX || this == MASTER
}

/**
 * Where everything on the menu and sub-screens sits.
 *
 * This exists so that the box a control is *drawn* into and the box it is
 * *tested* against are computed by the same code. The profile claim button used
 * to be a casualty of that being split: the drawn row came from a layout that
 * had overflowed the band, while the hit test used a differently scaled pill, so
 * a tap on CLAIM landed on BACK. One geometry, two consumers, no drift.
 *
 * Pure geometry only -- no GL, no game state. [MenuState] carries the state in.
 */
class MenuGeometry(
    private val text: MenuText,
    val screenWidth: Float,
    val screenHeight: Float,
    val scale: UiScale,
) {

    /** Pill width for [label]: the text plus the button's horizontal padding. */
    private fun pillW(label: String): Float = text.width(label) + 26f * scale.factor

    /** Pill height: the line box plus the button's vertical padding. */
    private fun pillH(label: String): Float = text.bodyLineHeight() + 16f * scale.factor

    /** 2-column button grid used by the main menu. */
    fun menuGridPos(i: Int): Pair<Float, Float> {
        // Row gap used to be capped at a flat 72px, which made buttons crowd
        // together on phones and stay tiny on tablets. Scale with the viewport
        // and let the pill height set the floor so rows never overlap.
        val rowHeight = pillH("W")
        val gap = maxOf(scale.gap(72f), rowHeight + scale.gap(10f))
        val startY = screenHeight * 0.64f
        val row = i / 2
        val col = i % 2
        val cx = screenWidth * (if (col == 0) 0.335f else 0.665f)
        return cx to (startY - row * gap)
    }

    /** EASY / NORMAL / HARD segmented controls, well clear of gesture bars. */
    fun difficultySegs(): Array<FloatArray> {
        val w = maxOf(scale.px(196f), pillW(Strings.t("normal")) + scale.px(10f))
        val h = pillH(Strings.t("normal"))
        val cy = screenHeight * 0.10f
        val gap = w + scale.px(18f)
        return arrayOf(
            floatArrayOf(screenWidth / 2f - gap, cy, w, h, 0f),
            floatArrayOf(screenWidth / 2f, cy, w, h, 1f),
            floatArrayOf(screenWidth / 2f + gap, cy, w, h, 2f),
        )
    }

    fun subScreenTitleCy(): Float = screenHeight * 0.84f

    fun backPill(): MenuRect = MenuRect(
        screenWidth / 2f,
        screenHeight * 0.08f,
        pillW(Strings.t("back")),
        pillH(Strings.t("back")),
    )

    /** Achievements pill on the profile screen, right of the back button. */
    fun achievementsPill(count: Int, total: Int): MenuRect {
        val label = "${Strings.t("achievements")} $count/$total"
        return MenuRect(
            screenWidth * 0.82f,
            backPill().cy,
            pillW(label),
            pillH(label),
        )
    }

    /** Measured body-text line height, so rows can be spaced by their real size. */
    fun subTextRowHeight(): Float = text.bodyLineHeight()

    /** Row height for rows that contain a pill, since the pill is the tallest thing in them. */
    fun subPillRowHeight(): Float = pillH("W")

    /**
     * Vertical space a sub-screen panel may occupy: below the title, above the
     * back pill. Previously the panel height was a fixed fraction of the screen
     * and the settings panel grew straight through the title.
     */
    fun subScreenBand(): Pair<Float, Float> {
        val titleH = text.titleLineHeight()
        val titleBottom = subScreenTitleCy() - titleH / 2f - scale.gap(12f)
        val back = backPill()
        val backTop = back.cy + back.h / 2f + scale.gap(12f)
        return titleBottom to backTop
    }

    /**
     * Row layout for a sub-screen. Every panel goes through this so rows are
     * spaced by the height of the text they actually draw, instead of by fixed
     * pixel constants that went stale once the font started scaling.
     */
    fun subLayout(rows: Int, pillRows: Boolean): SubScreenLayout {
        val (bandTop, bandBottom) = subScreenBand()
        return SubScreenLayoutFactory.create(
            screenW = screenWidth,
            screenH = screenHeight,
            bandTop = bandTop,
            bandBottom = bandBottom,
            rowHeight = if (pillRows) subPillRowHeight() else subTextRowHeight(),
            rows = rows,
            scale = scale.factor,
            padX = scale.gap(18f),
        )
    }

    /** Right-hand x for the value pills on a sub-screen row. */
    fun subPillCx(layout: SubScreenLayout): Float =
        layout.panelCx + layout.panelW / 2f - scale.gap(30f)

    /**
     * Profile rows: six stats, the daily line, the challenge summary and the
     * claim button.
     *
     * Text rows, not pill rows: nine pill-height rows cannot fit the band on a
     * 1440x3200 phone, and the overflow pushed the claim button underneath the
     * back button where every tap went "back" instead. Only the claim row draws
     * a pill, and it has the row padding to itself.
     */
    fun profileLayout(): SubScreenLayout = subLayout(rows = PROFILE_ROWS, pillRows = false)

    /** Claim button for the daily challenge on the profile screen. */
    fun profileClaimCy(): Float = profileLayout().rowY(PROFILE_ROWS - 1)

    fun claimPill(): MenuRect {
        val label = "${Strings.t("claim")} +${com.depthdiver.Challenge.REWARD}"
        return MenuRect(
            screenWidth / 2f,
            profileClaimCy(),
            pillW(label),
            pillH(label),
        )
    }

    fun achievementsLayout(): SubScreenLayout =
        subLayout(rows = com.depthdiver.Achievements.ALL.size, pillRows = false)

    fun leaderboardLayout(entries: Int): SubScreenLayout =
        subLayout(rows = (entries + 1).coerceAtLeast(2), pillRows = false)

    /** One header row of pearls, then one row per upgrade. */
    fun shopLayout(): SubScreenLayout =
        subLayout(rows = Profile.Upgrade.values().size + 1, pillRows = true)

    fun shopBuyLabel(u: Profile.Upgrade): String {
        val cost = Profile.upgradeCost(u)
        return if (cost == null) Strings.t("max") else "${Strings.t("buy")} $cost"
    }

    /** BUY pill for upgrade row [row], right-aligned so the buttons form one column. */
    fun shopBuyPill(u: Profile.Upgrade, layout: SubScreenLayout, row: Int): MenuRect {
        val label = shopBuyLabel(u)
        return MenuRect(
            layout.valueX() - pillW(label) / 2f,
            layout.rowY(row),
            pillW(label),
            pillH(label),
        )
    }

    /** Header, swatch, name, state, arrows plus the hint: six rows. */
    fun cosmeticsLayout(): SubScreenLayout = subLayout(rows = 6, pillRows = true)

    fun cosmeticsArrows(layout: SubScreenLayout): Pair<MenuRect, MenuRect> {
        val cy = layout.rowY(3)
        val w = pillW("<")
        val h = pillH("<")
        val left = MenuRect(layout.panelCx - layout.panelW * 0.25f, cy, w, h)
        val right = MenuRect(layout.panelCx + layout.panelW * 0.25f, cy, w, h)
        return left to right
    }

    fun cosmeticsActionPill(layout: SubScreenLayout): MenuRect {
        val label = Strings.t("equip")
        return MenuRect(
            layout.panelCx,
            layout.rowY(3),
            pillW(label),
            pillH(label),
        )
    }

    fun settingsLayout(): SubScreenLayout = subLayout(rows = Setting.values().size, pillRows = true)

    /** Width of the slider track on a settings row. */
    fun sliderW(layout: SubScreenLayout): Float = layout.panelW * 0.36f

    fun sliderCx(layout: SubScreenLayout): Float =
        layout.panelCx + layout.panelW / 2f - sliderW(layout) / 2f - scale.gap(14f)

    /**
     * Hit box for a settings row's control: the slider track, or the value pill.
     *
     * The toggle box is sized from the ON/OFF text that is actually drawn, not
     * from the row label, so the target is the pill the player can see.
     */
    fun settingControlRect(setting: Setting, layout: SubScreenLayout, enabled: Boolean): MenuRect {
        val cy = layout.rowY(setting.ordinal)
        return if (setting.isSlider) {
            MenuRect(sliderCx(layout), cy, sliderW(layout), subPillRowHeight())
        } else {
            val value = if (enabled) Strings.t("on") else Strings.t("off")
            MenuRect(
                subPillCx(layout),
                cy,
                pillW(value),
                pillH(value),
            )
        }
    }

    fun settingLabel(setting: Setting): String = when (setting) {
        Setting.MUSIC -> Strings.t("musicVolume")
        Setting.SFX -> Strings.t("sfxVolume")
        Setting.MASTER -> Strings.t("masterVolume")
        Setting.REDUCE_MOTION -> Strings.t("reduceMotion")
        Setting.HIGH_CONTRAST -> Strings.t("highContrast")
        Setting.SCREEN_SHAKE -> Strings.t("screenShake")
    }

    /** Caption above the difficulty segments, clear of the pill it labels. */
    fun difficultyCaptionY(): Float {
        val segs = difficultySegs()
        return com.depthdiver.game.MenuMetrics.captionAboveSegment(
            segs[0][1], segs[0][3], subTextRowHeight(), scale.gap(6f),
        )
    }

    private companion object {
        /** Six stats, the daily line, the challenge line, then the claim row. */
        const val PROFILE_ROWS = 9
    }
}
