package com.depthdiver.menu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.math.MathUtils
import com.depthdiver.Achievements
import com.depthdiver.Challenge
import com.depthdiver.Leaderboard
import com.depthdiver.Profile
import com.depthdiver.Widgets
import com.depthdiver.common.Strings
import com.depthdiver.game.SubScreenLayout
import kotlin.math.min

/**
 * Draws the main menu and the four sub-screens behind it.
 *
 * Holds no game state: positions come from [MenuGeometry] and per-frame values
 * from [MenuState]. The FBO blur is the one piece of GL plumbing that stays here
 * rather than in the geometry, because drawing into an offscreen target is a
 * rendering concern; the caller owns the buffer's lifetime.
 */
class MenuRenderer(
    private val batch: SpriteBatch,
    private val font: BitmapFont,
    private val titleFont: BitmapFont,
    private val pixel: Texture,
    private val playerTexture: Texture,
    private val screenCamera: OrthographicCamera,
) {

    fun drawMainMenu(state: MenuState, geometry: MenuGeometry, fbo: FrameBuffer?) {
        drawMenuBackgroundBlur(state, geometry, fbo)
        drawMenuContent(state, geometry)
    }

    /** Renders the animated menu background into a 1/4-res FBO, then composites it
     *  back upscaled with bilinear filtering (with a dim tint) to fake a soft blur
     *  behind the crisp menu content. */
    private fun drawMenuBackgroundBlur(state: MenuState, geometry: MenuGeometry, fbo: FrameBuffer?) {
        if (fbo == null) return
        batch.end()
        fbo.begin()
        Gdx.gl.glClearColor(0.02f, 0.12f, 0.25f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.projectionMatrix = screenCamera.combined
        batch.begin()
        drawMenuBackground(state, geometry)
        drawMenuVignette(geometry)
        batch.end()
        fbo.end()
        batch.projectionMatrix = screenCamera.combined
        batch.begin()
        batch.setColor(1f, 1f, 1f, 0.92f)
        batch.draw(fbo.colorBufferTexture, 0f, 0f, geometry.screenWidth, geometry.screenHeight)
        batch.setColor(0f, 0.03f, 0.10f, 0.22f)
        batch.draw(pixel, 0f, 0f, geometry.screenWidth, geometry.screenHeight)
        batch.setColor(Color.WHITE)
    }

    /**
     * The background every sub-screen sits on. Exposed because the caller draws
     * the blur before it hands over to a sub-screen.
     */
    fun drawBackground(state: MenuState, geometry: MenuGeometry, fbo: FrameBuffer?) {
        drawMenuBackgroundBlur(state, geometry, fbo)
    }

    private fun drawMenuContent(state: MenuState, geometry: MenuGeometry) {
        val pulse = 0.5f + 0.5f * MathUtils.sin(state.menuTime * 1.8f)
        val titleY = geometry.screenHeight * 0.86f
        titleFont.color = Color(0.12f, 0.5f, 0.95f, 0.22f + 0.15f * pulse)
        for (off in floatArrayOf(-3f, 3f)) {
            Widgets.text(batch, titleFont, Strings.t("menuTitle"), geometry.screenWidth / 2f + off, titleY)
        }
        titleFont.color = Color(0.4f + 0.5f * pulse, 0.87f, 1f, 1f)
        Widgets.text(batch, titleFont, Strings.t("menuTitle"), geometry.screenWidth / 2f, titleY)
        titleFont.color = Color(0.35f, 0.85f, 1f, 1f)
        batch.setColor(0.2f, 0.78f, 1f, 0.55f)
        batch.draw(pixel, geometry.screenWidth / 2f - 170f, titleY - 26f, 340f, 4f)
        batch.draw(pixel, geometry.screenWidth / 2f - 110f, titleY - 35f, 220f, 3f)
        batch.setColor(Color.WHITE)

        if (geometry.screenHeight >= 520f) {
            font.color = Color.CYAN
            Widgets.text(batch, font, Strings.t("menuSubtitle"), geometry.screenWidth / 2f, geometry.screenHeight * 0.74f)
        }
        font.color = Color.WHITE
        for ((i, label) in menuLabels(state).withIndex()) {
            val (cx, cy) = geometry.menuGridPos(i)
            Widgets.pill(batch, font, pixel, cx, cy, label, scale = state.scale.factor)
        }

        val segs = geometry.difficultySegs()
        font.color = Color(0.5f, 0.8f, 1f, 0.85f)
        // The caption belongs above the segments. Offset it by its own measured
        // height plus a scaled gap; the old fixed +18 sat inside the pill.
        Widgets.text(batch, font, Strings.t("difficulty"), geometry.screenWidth / 2f, geometry.difficultyCaptionY())
        font.color = Color.WHITE
        val names = listOf("easy", "normal", "hard")
        for (s in segs) {
            Widgets.segment(
                batch,
                font,
                pixel,
                s[0],
                s[1],
                s[2],
                s[3],
                Strings.t(names[s[4].toInt()]),
                selected = s[4].toInt() == state.difficulty,
            )
        }
    }

    fun menuLabels(state: MenuState): List<String> = listOf(
        Strings.t("play"),
        Strings.t("profile"),
        Strings.t("leaderboard"),
        Strings.t("shop"),
        Strings.t("settings"),
        if (state.muted) Strings.t("muteOn") else Strings.t("muteOff"),
        Strings.t("quit"),
    )

    private fun drawMenuVignette(geometry: MenuGeometry) {
        val edge = geometry.screenHeight * 0.06f
        batch.setColor(0f, 0.03f, 0.09f, 0.40f)
        batch.draw(pixel, 0f, geometry.screenHeight - edge, geometry.screenWidth, edge)
        batch.draw(pixel, 0f, 0f, geometry.screenWidth, edge)
        batch.draw(pixel, 0f, edge, edge, geometry.screenHeight - 2f * edge)
        batch.draw(pixel, geometry.screenWidth - edge, edge, edge, geometry.screenHeight - 2f * edge)
        batch.setColor(Color.WHITE)
    }

    private fun drawMenuBackground(state: MenuState, geometry: MenuGeometry) {
        val bubbleCount = 26
        for (i in 0 until bubbleCount) {
            val xFrac = (i * 37 % 100) / 100f
            val x = xFrac * geometry.screenWidth
            val speed = 20f + (i % 5) * 9f
            val size = 4f + (i % 4) * 3f
            val start = (i * 53 % 100) / 100f * (geometry.screenHeight + 80f)
            val y = (start + state.menuTime * speed) % (geometry.screenHeight + 80f) - 40f
            val alpha = 0.10f + (i % 3) * 0.05f
            batch.setColor(0.55f, 0.85f, 1f, alpha)
            batch.draw(pixel, x - size / 2f, y - size / 2f, size, size)
        }
        val dSize = min(geometry.screenWidth, geometry.screenHeight) * 0.108f
        val dx = geometry.screenWidth * 0.5f + MathUtils.sin(state.menuTime * 0.5f) * geometry.screenWidth * 0.16f
        val dy = geometry.screenHeight * 0.585f + MathUtils.sin(state.menuTime * 1.1f) * 12f
        batch.draw(playerTexture, dx - dSize / 2f, dy - dSize / 2f, dSize, dSize)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawSubScreenHeader(title: String, geometry: MenuGeometry, state: MenuState) {
        Widgets.text(batch, titleFont, title, geometry.screenWidth / 2f, geometry.subScreenTitleCy())
        val pill = geometry.backPill()
        Widgets.pill(batch, font, pixel, pill.cx, pill.cy, Strings.t("back"), scale = state.scale.factor)
    }

    fun drawProfileScreen(state: MenuState, geometry: MenuGeometry) {
        Widgets.text(batch, titleFont, Strings.t("profile"), geometry.screenWidth / 2f, geometry.subScreenTitleCy())
        val back = geometry.backPill()
        Widgets.pill(batch, font, pixel, back.cx, back.cy, Strings.t("back"), scale = state.scale.factor)
        val ach = geometry.achievementsPill(Achievements.count(), Achievements.ALL.size)
        Widgets.pill(batch, font, pixel, ach.cx, ach.cy, achLabelText(), scale = state.scale.factor)

        val layout = geometry.profileLayout()
        Widgets.panel(batch, pixel, layout.panelCx, layout.panelCy, layout.panelW, layout.panelH)

        val stats = listOf(
            "${Strings.t("best")} ${Strings.t("depth")}" to "${state.bestDepth.toInt()} m",
            "${Strings.t("best")} ${Strings.t("score")}" to "${state.bestScore}",
            Strings.t("pearls") to "${Profile.pearls()}",
            Strings.t("pearlsEarned") to "${Profile.lifetimePearls()}",
            Strings.t("dives") to "${Profile.dives()}",
            Strings.t("achievements") to "${Achievements.count()}/${Achievements.ALL.size}",
        )
        stats.forEachIndexed { i, (label, value) ->
            font.color = Color.WHITE
            Widgets.textLeft(batch, font, label, layout.labelX(), layout.rowBaseline(i))
            font.color = Color.GOLD
            Widgets.textRight(batch, font, value, layout.valueX(), layout.rowBaseline(i))
        }
        font.color = Color.WHITE

        val day = Profile.dailyDay()
        val activeCh = Challenge.activeFor(day)
        val chClaimed = Challenge.claimedFor(activeCh)
        val chMet = activeCh.met(state.bestDepth, Profile.bestRunPearls(), state.bestScore)

        font.color = Color.GOLD
        Widgets.text(
            batch,
            font,
            if (Profile.claimedDailyDay() == day) Strings.t("dailyClaimed") else Strings.t("dailyReady"),
            layout.panelCx,
            layout.rowY(stats.size),
        )
        val chText = if (chClaimed) {
            Strings.t("chDone")
        } else {
            activeCh.summary(state.bestDepth, Profile.bestRunPearls(), state.bestScore)
        }
        font.color = Color.CYAN
        Widgets.text(batch, font, chText, layout.panelCx, layout.rowY(stats.size + 1))

        if (!chClaimed) {
            val pill = geometry.claimPill()
            Widgets.pill(
                batch, font, pixel, pill.cx, pill.cy,
                "${Strings.t("claim")} +${Challenge.REWARD}",
                enabled = chMet, scale = state.scale.factor,
            )
        }
        font.color = Color.WHITE
    }

    private fun achLabelText(): String =
        "${Strings.t("achievements")} ${Achievements.count()}/${Achievements.ALL.size}"

    fun drawAchievementsScreen(state: MenuState, geometry: MenuGeometry) {
        drawSubScreenHeader(Strings.t("achievements"), geometry, state)
        val layout = geometry.achievementsLayout()
        Widgets.panel(batch, pixel, layout.panelCx, layout.panelCy, layout.panelW, layout.panelH)

        if (Achievements.count() == Achievements.ALL.size) {
            font.color = Color.GOLD
            Widgets.text(batch, font, Strings.t("allDone"), layout.panelCx, layout.panelTop - layout.rowHeight * 0.4f)
        }

        Achievements.ALL.forEachIndexed { i, def ->
            val unlocked = Achievements.isUnlocked(def)
            font.color = if (unlocked) Color.GOLD else Color(0.45f, 0.55f, 0.65f, 1f)
            Widgets.textLeft(batch, font, def.name, layout.labelX(), layout.rowBaseline(i))
            font.color = Color.WHITE
            Widgets.textRight(
                batch, font,
                if (unlocked) Strings.t("open") else Strings.t("locked"),
                layout.valueX(), layout.rowBaseline(i),
            )
        }
        font.color = Color.WHITE
    }

    fun drawLeaderboardScreen(state: MenuState, geometry: MenuGeometry) {
        drawSubScreenHeader(Strings.t("leaderboard"), geometry, state)
        val entries = Leaderboard.top()
        val layout = geometry.leaderboardLayout(entries.size)
        Widgets.panel(batch, pixel, layout.panelCx, layout.panelCy, layout.panelW, layout.panelH)

        val scoreX = layout.panelCx + layout.panelW / 4f
        val depthX = layout.valueX()
        val headerY = layout.rowBaseline(0)
        font.color = Color.CYAN
        Widgets.textLeft(batch, font, Strings.t("rank"), layout.labelX(), headerY)
        Widgets.textRight(batch, font, Strings.t("score"), scoreX, headerY)
        Widgets.textRight(batch, font, Strings.t("depth"), depthX, headerY)

        if (entries.isEmpty()) {
            font.color = Color.WHITE
            Widgets.text(batch, font, Strings.t("noRuns"), layout.panelCx, layout.rowY(1))
        } else {
            entries.forEachIndexed { i, e ->
                val y = layout.rowBaseline(i + 1)
                font.color = Color.WHITE
                Widgets.textLeft(batch, font, "${i + 1}.", layout.labelX(), y)
                Widgets.textRight(batch, font, "${e.score}", scoreX, y)
                Widgets.textRight(batch, font, "${e.depth.toInt()} m", depthX, y)
            }
        }
        font.color = Color.WHITE
    }

    fun drawShopScreen(state: MenuState, geometry: MenuGeometry) {
        drawSubScreenHeader(Strings.t("shop"), geometry, state)
        val upgrades = Profile.Upgrade.values()
        val layout = geometry.shopLayout()
        Widgets.panel(batch, pixel, layout.panelCx, layout.panelCy, layout.panelW, layout.panelH)

        font.color = Color.GOLD
        Widgets.text(batch, font, "${Strings.t("pearls")}: ${Profile.pearls()}", layout.panelCx, layout.rowY(0))

        upgrades.forEachIndexed { i, u ->
            val label = geometry.shopBuyLabel(u)
            val affordable = !Profile.isMaxed(u) && Profile.pearls() >= (Profile.upgradeCost(u) ?: 0)
            val pill = geometry.shopBuyPill(u, layout, i + 1)

            font.color = Color.WHITE
            Widgets.textLeft(batch, font, u.label, layout.labelX(), layout.rowBaseline(i + 1))
            font.color = Color.CYAN
            Widgets.textRight(
                batch, font, "${Strings.t("level")} ${Profile.level(u)}/${Profile.MAX_LEVEL}",
                pill.cx - pill.w / 2f - state.scale.gap(12f), layout.rowBaseline(i + 1),
            )
            Widgets.pill(batch, font, pixel, pill.cx, pill.cy, label, enabled = affordable, scale = state.scale.factor)
        }
        font.color = Color.WHITE
    }

    fun drawSettingsScreen(state: MenuState, geometry: MenuGeometry, volumes: SettingVolumes, enabled: SettingFlags) {
        drawSubScreenHeader(Strings.t("settings"), geometry, state)
        val layout = geometry.settingsLayout()
        Widgets.panel(batch, pixel, layout.panelCx, layout.panelCy, layout.panelW, layout.panelH)

        for (setting in Setting.values()) {
            font.color = Color.WHITE
            Widgets.textLeft(
                batch, font, geometry.settingLabel(setting),
                layout.labelX(), layout.rowBaseline(setting.ordinal),
            )
            if (setting.isSlider) {
                val value = volumes.of(setting)
                Widgets.slider(
                    batch, font, pixel,
                    geometry.sliderCx(layout), layout.rowY(setting.ordinal), geometry.sliderW(layout),
                    value, state.scale.factor,
                )
                font.color = Color.WHITE
                Widgets.text(
                    batch, font, "${(value * 100).toInt()}%",
                    geometry.sliderCx(layout), layout.rowY(setting.ordinal),
                )
            } else {
                val on = enabled.of(setting)
                val r = geometry.settingControlRect(setting, layout, on)
                Widgets.pill(batch, font, pixel, r.cx, r.cy, if (on) Strings.t("on") else Strings.t("off"), enabled = on, scale = state.scale.factor)
            }
        }
        font.color = Color.WHITE
    }
}

/** Slider positions, read once per frame so the drawn percentage and the stored
 *  value cannot disagree. */
class SettingVolumes(
    private val music: Float,
    private val sfx: Float,
    private val master: Float,
) {
    fun of(setting: Setting): Float = when (setting) {
        Setting.MUSIC -> music
        Setting.SFX -> sfx
        Setting.MASTER -> master
        else -> 0f
    }
}

/** Toggle positions, read once per frame for the same reason. */
class SettingFlags(
    private val reduceMotion: Boolean,
    private val highContrast: Boolean,
    private val screenShake: Boolean,
) {
    fun of(setting: Setting): Boolean = when (setting) {
        Setting.REDUCE_MOTION -> reduceMotion
        Setting.HIGH_CONTRAST -> highContrast
        Setting.SCREEN_SHAKE -> screenShake
        else -> true
    }
}
