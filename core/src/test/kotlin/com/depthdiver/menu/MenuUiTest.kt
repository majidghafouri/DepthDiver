package com.depthdiver.menu

import com.depthdiver.Profile
import com.depthdiver.TestPreferences
import com.depthdiver.game.UiScale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every control on every screen is reachable by tapping it.
 *
 * This is a UI test for a game whose UI is drawn by libGDX on the GPU, so
 * Espresso and Robolectric are both the wrong tool: Espresso can only address
 * Android Views, and there are none here, and Robolectric cannot hand the game a
 * GL context to render into. What can be tested without a device is the part
 * that actually breaks -- the box a control is drawn into, and the order the
 * handler tests those boxes in. Every bug found in this area has been one of
 * those two things disagreeing.
 *
 * The screen orders below mirror the order of the checks in DepthDiverGame's
 * handlers, because the order is half the contract: the first box a tap lands in
 * wins, so a control drawn on top of another is unreachable no matter how
 * correctly it was drawn.
 */
class MenuUiTest {

    @BeforeTest
    fun setUp() {
        // The shop reads upgrade costs and levels out of Profile.
        Profile.prefsOverride = TestPreferences("depthdiver")
    }

    @AfterTest
    fun tearDown() {
        Profile.prefsOverride = null
    }

    /** One tappable thing, in the order the screen's handler checks them. */
    private data class Control(val name: String, val x: Float, val y: Float, val w: Float, val h: Float) {
        fun contains(px: Float, py: Float): Boolean =
            px >= x - w / 2f && px <= x + w / 2f && py >= y - h / 2f && py <= y + h / 2f
    }

    private class FakeText(private val bodySize: Int, private val titleSize: Int) : MenuText {
        override fun width(text: String): Float = 0.55f * bodySize * text.length
        override fun bodyLineHeight(): Float = bodySize * 1.15f
        override fun titleLineHeight(): Float = titleSize * 1.15f
    }

    private fun geometry(w: Float, h: Float): MenuGeometry {
        val scale = UiScale.forScreen(w, h)
        val body = maxOf(minOf((h / 30f * scale.factor).toInt(), 64), 16)
        val title = maxOf(minOf((h / 9f * scale.factor).toInt(), 120), 36)
        return MenuGeometry(FakeText(body, title), w, h, scale)
    }

    private fun from(r: MenuRect, name: String) = Control(name, r.cx, r.cy, r.w, r.h)

    private val labels = listOf("PLAY", "PROFILE", "LEADERBOARD", "SHOP", "SETTINGS", "MUTE", "QUIT")

    /** Main menu: difficulty segments are tested before the buttons. */
    private fun mainMenuControls(w: Float, h: Float): List<Control> {
        val g = geometry(w, h)
        val out = mutableListOf<Control>()
        // Widgets.segment takes centres, so the array already is centre-based.
        g.difficultySegs().forEachIndexed { i, s ->
            out += Control("difficulty-$i", s[0], s[1], s[2], s[3])
        }
        labels.forEachIndexed { i, label ->
            val (cx, cy) = g.menuGridPos(i)
            val size = bodySize(w, h)
            val cw = 0.55f * size * label.length + 26f * UiScale.forScreen(w, h).factor
            val ch = 1.15f * size + 16f * UiScale.forScreen(w, h).factor
            out += Control("menu-$i:$label", cx, cy, cw, ch)
        }
        return out
    }

    /** Profile: back, then the achievements pill, then the claim pill. */
    private fun profileControls(w: Float, h: Float, claimable: Boolean): List<Control> {
        val g = geometry(w, h)
        val out = mutableListOf(from(g.backPill(), "back"))
        out += from(g.achievementsPill(1, 9), "achievements")
        if (claimable) out += from(g.claimPill(), "claim")
        return out
    }

    private fun settingsControls(w: Float, h: Float): List<Control> {
        val g = geometry(w, h)
        val layout = g.settingsLayout()
        val back = g.backPill()
        val out = mutableListOf(Control("back", back.cx, back.cy, back.w, back.h))
        Setting.values().forEach { s ->
            val enabled = s != Setting.REDUCE_MOTION
            val r = g.settingControlRect(s, layout, enabled)
            out += Control(s.name.lowercase(), r.cx, r.cy, r.w, r.h)
        }
        return out
    }

    private fun shopControls(w: Float, h: Float): List<Control> {
        val g = geometry(w, h)
        val layout = g.shopLayout()
        val back = g.backPill()
        val out = mutableListOf(Control("back", back.cx, back.cy, back.w, back.h))
        Profile.Upgrade.values().forEachIndexed { i, u ->
            val p = g.shopBuyPill(u, layout, i + 1)
            out += Control("buy-$u", p.cx, p.cy, p.w, p.h)
        }
        return out
    }

    private fun bodySize(w: Float, h: Float): Int {
        val scale = UiScale.forScreen(w, h)
        return maxOf(minOf((h / 30f * scale.factor).toInt(), 64), 16)
    }

    private val viewports = listOf(
        800f to 480f, 1280f to 720f, 1600f to 900f, 1920f to 1080f,
        2340f to 1080f, 2400f to 1080f, 2560f to 1080f, 2560f to 1600f,
        3200f to 1440f, 2048f to 1536f, 2176f to 1812f, 3840f to 2160f,
    )

    /** The first control whose box contains the point, which is what the game does. */
    private fun resolve(controls: List<Control>, x: Float, y: Float): String? =
        controls.firstOrNull { it.contains(x, y) }?.name

    private fun assertAllReachable(screen: String, w: Float, h: Float, controls: List<Control>) {
        for (target in controls) {
            val winner = resolve(controls, target.x, target.y)
            if (winner != target.name) {
                fail(
                    "$screen at ${w.toInt()}x${h.toInt()}: tapping the centre of '${target.name}' " +
                        "activates '$winner' instead. Either the boxes overlap or an earlier " +
                        "check is claiming the tap.",
                )
            }
        }
    }

    private fun assertNoFullOcclusion(screen: String, w: Float, h: Float, controls: List<Control>) {
        for (a in controls) {
            for (b in controls) {
                if (a === b) continue
                // Sample a small grid inside a; if no sample resolves to a, then a
                // is entirely behind something.
                var samples = 0
                var hits = 0
                var sx = -1
                var sy = -1
                var steps = 4
                while (sx < steps) {
                    var syy = 0
                    while (syy < steps) {
                        val px = a.x - a.w / 2f + a.w * (sx + 0.5f) / steps
                        val py = a.y - a.h / 2f + a.h * (syy + 0.5f) / steps
                        samples++
                        if (resolve(controls, px, py) == a.name) hits++
                        syy++
                    }
                    sx++
                }
                if (samples > 0 && hits == 0) {
                    fail(
                        "$screen at ${w.toInt()}x${h.toInt()}: '${a.name}' is completely " +
                            "unreachable, fully covered by '$b'.",
                    )
                }
            }
        }
    }

    private fun assertOnScreen(screen: String, w: Float, h: Float, controls: List<Control>) {
        for (c in controls) {
            assertTrue(
                onScreen(c, w, h),
                "$screen at ${w.toInt()}x${h.toInt()}: '${c.name}' is off screen " +
                    "(x=${c.x} w=${c.w}, y=${c.y} h=${c.h})",
            )
        }
    }

    private fun onScreen(c: Control, w: Float, h: Float): Boolean =
        c.x - c.w / 2f >= -0.5f && c.x + c.w / 2f <= w + 0.5f &&
            c.y - c.h / 2f >= -0.5f && c.y + c.h / 2f <= h + 0.5f

    @Test
    fun everyMainMenuButtonIsReachableOnEveryDevice() {
        for ((w, h) in viewports) {
            val controls = mainMenuControls(w, h)
            assertAllReachable("main menu", w, h, controls)
            assertNoFullOcclusion("main menu", w, h, controls)
            assertOnScreen("main menu", w, h, controls)
        }
    }

    @Test
    fun everySettingsControlIsReachableOnEveryDevice() {
        for ((w, h) in viewports) {
            val controls = settingsControls(w, h)
            assertAllReachable("settings", w, h, controls)
            assertNoFullOcclusion("settings", w, h, controls)
            assertOnScreen("settings", w, h, controls)
        }
    }

    @Test
    fun everyShopControlIsReachableOnEveryDevice() {
        for ((w, h) in viewports) {
            val controls = shopControls(w, h)
            assertAllReachable("shop", w, h, controls)
            assertNoFullOcclusion("shop", w, h, controls)
            assertOnScreen("shop", w, h, controls)
        }
    }

    @Test
    fun theProfileClaimIsReachableOnEveryDevice() {
        // The reported bug, asserted across the whole device matrix instead of
        // the one phone it was found on.
        for ((w, h) in viewports) {
            val controls = profileControls(w, h, claimable = true)
            assertAllReachable("profile", w, h, controls)
            assertNoFullOcclusion("profile", w, h, controls)
            assertOnScreen("profile", w, h, controls)
        }
    }

    @Test
    fun theProfileClaimDoesNotCollideWithBackOnAnyDevice() {
        for ((w, h) in viewports) {
            val g = geometry(w, h)
            val back = g.backPill()
            val claim = g.claimPill()
            val backBottom = back.cy - back.h / 2f
            val claimTop = claim.cy - claim.h / 2f
            val overlap = minOf(backBottom, claimTop + claim.h) - maxOf(back.cy - back.h / 2f, claimTop)
            assertTrue(
                claimTop >= backBottom,
                "at ${w.toInt()}x${h.toInt()} the claim and back boxes overlap by ${abs(overlap)}px",
            )
        }
    }

    @Test
    fun tappingTheEdgeOfAControlStillActivatesIt() {
        // A control that is only tappable in its exact centre is a control that
        // feels broken on a phone.
        for ((w, h) in viewports) {
            val controls = settingsControls(w, h)
            for (c in controls) {
                for ((dx, dy) in listOf(0f to 0f, 0.25f to 0f, 0.4f to 0f, -0.25f to 0f, -0.4f to 0f)) {
                    val px = c.x + c.w / 2f * dx
                    val py = c.y + c.h / 2f * dy
                    assertTrue(
                        resolve(controls, px, py) == c.name,
                        "settings at ${w.toInt()}x${h.toInt()}: tapping ${dx * 100}% across " +
                            "'${c.name}' activates '${resolve(controls, px, py)}'",
                    )
                }
            }
        }
    }
}
