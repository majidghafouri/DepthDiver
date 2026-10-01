package com.depthdiver.menu

import com.depthdiver.game.UiScale
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The menu and its sub-screens had two bugs that were both "the box and the tap
 * disagreed", so these are the properties that have to hold.
 *
 * Geometry is exercised directly rather than through a rendered frame: the point
 * of the extraction was that the box a control is drawn into and the box it is
 * tested against come from one function, and that is only checkable if the
 * function can be called on its own.
 */
class MenuGeometryTest {

    /**
     * Stands in for the real fonts at the size the game would generate for this
     * viewport, so the geometry sees the same numbers it sees on a device.
     */
    private class FakeText(private val bodySize: Int, private val titleSize: Int) : MenuText {
        override fun width(text: String): Float = 0.55f * bodySize * text.length
        override fun bodyLineHeight(): Float = bodySize * 1.15f
        override fun titleLineHeight(): Float = titleSize * 1.15f
    }

    private fun geometry(w: Float, h: Float): MenuGeometry {
        val scale = UiScale.forScreen(w, h)
        val body = maxOf(min((h / 30f * scale.factor).toInt(), 64), 16)
        val title = maxOf(min((h / 9f * scale.factor).toInt(), 120), 36)
        return MenuGeometry(FakeText(body, title), w, h, scale)
    }

    /** Pill size for a label, matching what the geometry measures. */
    private fun pill(label: String, size: Int, scale: UiScale): Pair<Float, Float> =
        (0.55f * size * label.length + 26f * scale.factor) to (1.15f * size + 16f * scale.factor)

    private val landscape = listOf(
        800f to 480f, 1280f to 720f, 2340f to 1080f, 2400f to 1080f,
        3200f to 1440f, 2048f to 1536f, 2176f to 1812f, 2560f to 1600f,
    )

    @Test
    fun theClaimPillNeverSitsUnderneathTheBackButton() {
        // The reported bug: on a 1440x3200 phone the nine rows were laid out as
        // pill rows, overflowed the band, and pushed CLAIM underneath BACK, so
        // tapping CLAIM navigated back to the menu instead of claiming.
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val back = g.backPill()
            val claim = g.claimPill()
            val backBottom = back.cy - back.h / 2f
            val claimTop = claim.cy - claim.h / 2f
            assertTrue(
                "${w}x$h: claim top $claimTop is under the back pill bottom $backBottom",
                claimTop > backBottom,
            )
        }
    }

    @Test
    fun everySubScreenPanelStaysInsideItsBand() {
        // Panels are anchored to the top of the band so a short list sits under
        // its caption, but that is only safe while the panel also stops above the
        // back pill.
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val (bandTop, bandBottom) = g.subScreenBand()
            val layouts = mapOf(
                "profile" to g.profileLayout(),
                "achievements" to g.achievementsLayout(),
                "leaderboard-empty" to g.leaderboardLayout(0),
                "leaderboard-full" to g.leaderboardLayout(5),
                "shop" to g.shopLayout(),
                "settings" to g.settingsLayout(),
            )
            for ((name, l) in layouts) {
                assertTrue(
                    "${w}x$h $name: panel top ${l.panelTop} is past the band top $bandTop",
                    l.panelTop <= bandTop + 0.5f,
                )
                assertTrue(
                    "${w}x$h $name: panel bottom ${l.panelBottom} overlaps the back pill at $bandBottom",
                    l.panelBottom >= bandBottom - 0.5f,
                )
            }
        }
    }

    @Test
    fun rowsInEveryPanelDoNotOverlap() {
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val checks = listOf(
                Triple("profile", g.profileLayout(), 9),
                Triple("achievements", g.achievementsLayout(), 9),
                Triple("settings", g.settingsLayout(), Setting.values().size),
                Triple("shop", g.shopLayout(), 6),
            )
            for ((name, l, rows) in checks) {
                assertTrue(
                    "${w}x$h $name: ${rows} rows do not fit (gap=${l.gap} row=${l.rowHeight} band=${g.subScreenBand().first - g.subScreenBand().second})",
                    l.rowsFit(rows),
                )
            }
        }
    }

    @Test
    fun theDifficultyCaptionClearsTheSegments() {
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val seg = g.difficultySegs()[0]
            val segTop = seg[1] + seg[3] / 2f
            val captionH = g.subTextRowHeight()
            val captionBottom = g.difficultyCaptionY() - captionH / 2f
            assertTrue(
                "${w}x$h: caption bottom $captionBottom is inside the segment (top $segTop)",
                captionBottom >= segTop,
            )
        }
    }

    @Test
    fun theSettingsToggleTargetIsThePillThatIsDrawn() {
        // Sized from the ON/OFF text, not the row label, so the touch target is
        // the visible control. It must not be wider than the label it replaced,
        // which is what it is there to replace.
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val layout = g.settingsLayout()
            val scale = UiScale.forScreen(w, h)
            val size = maxOf(min((h / 30f * scale.factor).toInt(), 64), 16)
            for (setting in Setting.values()) {
                if (setting.isSlider) continue
                val on = g.settingControlRect(setting, layout, true)
                val off = g.settingControlRect(setting, layout, false)
                assertTrue("toggle width collapsed for $setting", on.w > 0f && off.w > 0f)
                val labelW = pill(g.settingLabel(setting), size, scale).first
                assertTrue(
                    "${w}x$h $setting: target ${on.w} is not narrower than its label $labelW",
                    on.w < labelW,
                )
                // And it is exactly the ON/OFF pill, so the touch target is the
                // control the player can see.
                assertTrue(
                    "${w}x$h $setting: ON target ${on.w} != the drawn pill",
                    kotlin.math.abs(on.w - pill("on", size, scale).first) < 0.5f,
                )
            }
        }
    }

    @Test
    fun slidersSitInsideThePanel() {
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val layout = g.settingsLayout()
            for (setting in Setting.values().filter { it.isSlider }) {
                val r = g.settingControlRect(setting, layout, true)
                val left = r.cx - r.w / 2f
                val right = r.cx + r.w / 2f
                val panelLeft = layout.panelCx - layout.panelW / 2f
                val panelRight = layout.panelCx + layout.panelW / 2f
                assertTrue("${w}x$h $setting: slider overflows the panel", left >= panelLeft && right <= panelRight)
            }
        }
    }

    @Test
    fun menuButtonsDoNotOverlapEachOther() {
        for ((w, h) in landscape) {
            val g = geometry(w, h)
            val scale = UiScale.forScreen(w, h)
            val size = maxOf(min((h / 30f * scale.factor).toInt(), 64), 16)
            // Every slot, not a sample: the grid grew a ninth button when friend
            // runs were added, and a guard that stops checking at seven is how an
            // overlapping button ships.
            val boxes = (0..8).map { i ->
                val (cx, cy) = g.menuGridPos(i)
                val label = if (i == 7) "MUTE" else "BUTTON"
                val (bw, bh) = pill(label, size, scale)
                listOf(cx, cy, bw, bh)
            }
            for (i in boxes.indices) {
                for (j in i + 1 until boxes.size) {
                    val a = boxes[i]; val b = boxes[j]
                    val overlapX = kotlin.math.abs(a[0] - b[0]) < (a[2] + b[2]) / 2f
                    val overlapY = kotlin.math.abs(a[1] - b[1]) < (a[3] + b[3]) / 2f
                    assertTrue("${w}x$h: buttons $i and $j overlap", !(overlapX && overlapY))
                }
            }
        }
    }
}
