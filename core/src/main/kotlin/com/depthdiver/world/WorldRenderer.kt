package com.depthdiver.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.depthdiver.entity.Hazard
import com.depthdiver.game.WORLD_WIDTH_METERS
import com.depthdiver.entity.Pickup

/**
 * Draws the world: the water gradient, the light shafts, the drifting fish, the
 * entities, the particles and the diver.
 *
 * Nothing here mutates game state. The one thing it does move is the world
 * camera, and only to apply screen shake -- it puts the camera back before it
 * returns, exactly as it found it, because the HUD is drawn in the same frame
 * and must not inherit the offset.
 */
class WorldRenderer(
    private val batch: SpriteBatch,
    private val worldCamera: OrthographicCamera,
    private val font: BitmapFont,
    private val textures: WorldTextures,
) {

    fun render(state: WorldViewState) {
        drawBackground(state)
        val shaken = applyShake(state)
        if (shaken) {
            worldCamera.update()
            batch.projectionMatrix = worldCamera.combined
        }

        batch.setColor(1f, 1f, 1f, 1f)
        drawParticles(state)
        drawHazards(state)
        drawPickups(state)

        batch.draw(
            textures.player,
            state.playerX - state.playerRadius,
            state.playerY - state.playerRadius,
            state.playerRadius * 2f,
            state.playerRadius * 2f,
        )

        if (shaken) restoreCamera(state)
    }

    /** Returns true when the camera was displaced and has to be restored. */
    private fun applyShake(state: WorldViewState): Boolean {
        val shake = state.shake ?: return false
        if (shake.timer <= 0f) return false
        val duration = shake.duration.coerceAtLeast(0.0001f)
        val progress = (1f - shake.timer / duration).coerceIn(0f, 1f)
        val current = shake.intensity * (1f - progress * 0.7f)
        worldCamera.position.x += MathUtils.random(-current, current)
        worldCamera.position.y += MathUtils.random(-current, current)
        return true
    }

    private fun restoreCamera(state: WorldViewState) {
        worldCamera.position.set(state.camera.xMeters, state.camera.yMeters, 0f)
        worldCamera.update()
        batch.projectionMatrix = worldCamera.combined
    }

    private fun drawParticles(state: WorldViewState) {
        val pixel = textures.pixel
        for (p in state.particles) {
            batch.setColor(p.color.r, p.color.g, p.color.b, p.life / p.maxLife)
            batch.draw(pixel, p.x - p.size / 2f, p.y - p.size / 2f, p.size, p.size)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawHazards(state: WorldViewState) {
        val pixel = textures.pixel
        for (hazard in state.hazards) {
            val isBoss = hazard is Hazard.Shark && hazard.isBoss
            if (isBoss) batch.setColor(1f, 0.45f, 0.4f, 1f)
            val r = hazard.rect
            when (hazard) {
                is Hazard.Rock -> batch.draw(textures.rock, r.x, r.y, r.width, r.height)
                is Hazard.Mine -> batch.draw(textures.mine, r.x, r.y, r.width, r.height)
                is Hazard.Jellyfish -> batch.draw(textures.jellyfish, r.x, r.y, r.width, r.height)
                is Hazard.Shark -> {
                    if (hazard.isBoss) drawBossTelegraph(hazard, state)
                    batch.draw(textures.shark, r.x, r.y, r.width, r.height)
                    if (hazard.isBoss) drawBossHealthBar(hazard)
                }
                is Hazard.Eel ->
                    if (hazard.dir > 0) {
                        batch.draw(textures.eel, r.x, r.y, r.width, r.height)
                    } else {
                        // Negative width flips the sprite to face the other way.
                        batch.draw(textures.eel, r.x + r.width, r.y, -r.width, r.height)
                    }
                is Hazard.Angler -> {
                    batch.draw(textures.angler, r.x, r.y, r.width, r.height)
                    val pulse = 0.35f + 0.25f * MathUtils.sin(state.elapsed * 3f + hazard.phase)
                    batch.setColor(1f, 0.9f, 0.5f, pulse)
                    batch.draw(pixel, r.x + r.width * 0.85f - 0.3f, r.y + r.height * 0.8f - 0.3f, 0.6f, 0.6f)
                }
                is Hazard.Vortex -> {
                    val spin = 1f + 0.08f * MathUtils.sin(state.elapsed * 2.5f + hazard.phase)
                    val d = hazard.radius * spin
                    batch.setColor(1f, 1f, 1f, 0.55f)
                    batch.draw(
                        textures.vortex,
                        r.x + r.width / 2f - d,
                        r.y + r.height / 2f - d,
                        d * 2f,
                        d * 2f,
                    )
                }
            }
            batch.setColor(1f, 1f, 1f, 1f)
        }
    }

    private fun drawPickups(state: WorldViewState) {
        for (pickup in state.pickups) {
            if (pickup.collected) continue
            val tex = if (pickup is Pickup.OxygenTank) textures.oxygen else textures.pearl
            batch.draw(tex, pickup.rect.x, pickup.rect.y, pickup.rect.width, pickup.rect.height)
        }
    }

    /**
     * Wind-up marker for the boss's next attack.
     *
     * Drawn in world space, under the shark, so it is part of the world rather
     * than an overlay.
     */
    private fun drawBossTelegraph(boss: Hazard.Shark, state: WorldViewState) {
        val cx = boss.rect.x + boss.rect.width / 2f
        val cy = boss.rect.y + boss.rect.height / 2f
        val windup = when (boss.attackPattern) {
            Hazard.BossPattern.CHARGE -> 1.2f
            Hazard.BossPattern.SWEEP -> 1.0f
            Hazard.BossPattern.DIVE -> 1.5f
            Hazard.BossPattern.PROJECTILE -> 1.0f
            else -> 0f
        }
        if (windup <= 0f || boss.attackTimer >= windup) return

        val pixel = textures.pixel
        val progress = (boss.attackTimer / windup).coerceIn(0f, 1f)
        val intensity = 0.3f + 0.7f * progress
        val pulse = 0.5f + 0.5f * MathUtils.sin(state.elapsed * 20f * progress)

        when (boss.attackPattern) {
            Hazard.BossPattern.CHARGE -> {
                val dir = if (state.playerX > cx) 1f else -1f
                val targetX = if (dir > 0f) WORLD_WIDTH_METERS + 5f else -5f
                val ex = cx + dir * (boss.rect.width / 2f + 1.5f + pulse * 0.5f)
                val ey = cy - boss.rect.height / 2f - 1.5f
                batch.setColor(1f, 0.2f, 0.1f, intensity)
                font.color = Color(1f, 0.2f, 0.1f, intensity)
                font.draw(batch, "!", ex - 0.3f, ey + 0.8f)
                batch.setColor(1f, 0.1f, 0.05f, intensity * 0.5f)
                batch.draw(pixel, cx, cy, (targetX - cx) * progress, boss.rect.height)
            }
            Hazard.BossPattern.SWEEP -> {
                val sweepWidth = WORLD_WIDTH_METERS * 0.8f
                val startX = boss.rect.width / 2f + 0.5f
                val endX = WORLD_WIDTH_METERS - startX
                batch.setColor(1f, 0.5f, 0.1f, intensity * 0.4f)
                batch.draw(pixel, startX, boss.rect.y - 0.2f, sweepWidth, boss.rect.height + 0.4f)
                val arrowX = cx + (endX - startX) * progress - startX
                batch.setColor(1f, 0.7f, 0.2f, intensity)
                batch.draw(pixel, arrowX - 0.5f, cy - 0.5f, 1f, 1f)
            }
            Hazard.BossPattern.DIVE -> {
                val warningY = boss.rect.y - boss.rect.height * 2f
                batch.setColor(1f, 0.15f, 0.05f, intensity * 0.6f)
                batch.draw(pixel, 0f, warningY - 1f, WORLD_WIDTH_METERS, 2f + progress * 5f)
                val ey = warningY + progress * 5f
                batch.setColor(1f, 0.2f, 0.1f, intensity)
                font.color = Color(1f, 0.2f, 0.1f, intensity)
                font.draw(batch, "⚡", cx - 0.4f, ey)
            }
            Hazard.BossPattern.PROJECTILE -> {
                // All three arcs draw at the same x. The per-arc angle and
                // targetX are computed but unused, so the telegraph reads as a
                // single bar rather than a spread. Kept exactly as it was: this
                // is a move, and changing a boss's look inside a refactor is how
                // behaviour changes slip through unreviewed. It is a real bug,
                // but it is a separate change with its own decision.
                for (i in 0 until 3) {
                    val targetY = boss.rect.y - 10f
                    batch.setColor(0.8f, 0.4f, 0.1f, intensity * 0.4f)
                    batch.draw(pixel, cx - 0.2f, boss.rect.y - 0.2f, 0.4f, (targetY - boss.rect.y) * progress)
                }
            }
            else -> {}
        }
    }

    private fun drawBossHealthBar(boss: Hazard.Shark) {
        val w = boss.rect.width + 2f
        val h = 0.35f
        val x = boss.rect.x - 1f
        val y = boss.rect.y + boss.rect.height + 0.4f
        val frac = (boss.health / boss.maxHealth).coerceIn(0f, 1f)
        batch.setColor(0f, 0f, 0f, 0.6f)
        batch.draw(textures.pixel, x, y, w, h)
        batch.setColor(if (frac > 0.5f) Color.GREEN else if (frac > 0.25f) Color.YELLOW else Color.RED)
        batch.draw(textures.pixel, x, y, w * frac, h)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /** The water gradient, drifting streaks, ambient fish and surface light. */
    private fun drawBackground(state: WorldViewState) {
        val pixel = textures.pixel
        val water = state.water
        val camera = state.camera
        val spec = state.spec
        val margin = 1f
        val left = spec.worldLeft(camera) - margin
        val top = spec.worldTop(camera) + margin
        val visibleWidth = spec.viewWidthMeters + margin * 2f
        val visibleHeight = spec.viewHeightMeters + margin * 2f

        val bands = 16
        val bandH = visibleHeight / bands
        for (i in 0 until bands) {
            val t = (i + 1f) / bands
            batch.setColor(
                water.topRed + (water.bottomRed - water.topRed) * t,
                water.topGreen + (water.bottomGreen - water.topGreen) * t,
                water.topBlue + (water.bottomBlue - water.topBlue) * t,
                1f,
            )
            batch.draw(pixel, left, top - (i + 1) * bandH - 0.1f, visibleWidth, bandH + 0.2f)
        }

        for (i in 0 until 5) {
            val x = left + ((i * 31) % 100) / 100f * visibleWidth
            val speed = 1.3f + (i % 3) * 0.7f
            val span = visibleHeight + 5f
            val start = ((i * 47) % 100) / 100f * span
            val y = top - (start + state.elapsed * speed) % span - 2.5f
            batch.setColor(1f, 1f, 1f, 0.045f)
            batch.draw(pixel, x - 3.5f, y - 0.05f, 7f, 0.1f)
        }

        drawAmbientFish(state)

        val surface = surfaceFactor(state.depth)
        if (surface > 0.05f) {
            for (i in 0 until 4) {
                val sway = MathUtils.sin(state.elapsed * 0.35f + i * 1.3f) * 0.7f
                val baseX = left + visibleWidth * (0.16f + i * 0.24f) + sway
                val rayH = visibleHeight * (0.16f + (i % 2) * 0.06f)
                val segments = 6
                for (s in 0 until segments) {
                    val endT = (s + 1) / segments.toFloat()
                    val alpha = 0.05f * surface * (1f - endT * 0.85f)
                    val w = 1.3f - endT * 0.6f
                    batch.setColor(0.75f, 0.95f, 1f, alpha)
                    batch.draw(pixel, baseX - w / 2f, top - rayH * endT, w, rayH * (endT - s / segments.toFloat()) + 0.05f)
                }
            }
            batch.setColor(1f, 1f, 1f, 0.07f * surface)
            batch.draw(pixel, left, top - 2f, visibleWidth, 2f)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawAmbientFish(state: WorldViewState) {
        val spec = state.spec
        val camera = state.camera
        val left = spec.worldLeft(camera)
        val right = spec.worldRight(camera)
        val bottom = spec.worldBottom(camera)
        val visibleWidth = right - left
        val visibleHeight = spec.worldTop(camera) - bottom
        val scale = if (visibleWidth >= 60f) 1.15f else 0.9f
        for (i in 0 until 9) {
            val laneFrac = ((i * 29) % 100) / 100f
            val baseY = bottom + visibleHeight * (0.08f + laneFrac * 0.78f)
            val speed = 1f + (i % 4) * 0.45f
            val span = visibleWidth + 9f
            val dir = if ((i % 2) == 0) 1 else -1
            val cx = if (dir == 1) {
                left + (state.elapsed * speed % span) - 4.5f
            } else {
                left + span - (state.elapsed * speed % span) - 4.5f
            }
            val cy = baseY + MathUtils.sin(state.elapsed * 1.1f + i * 2.1f) * 0.35f
            val size = (1.1f + (i % 3) * 0.35f) * scale
            batch.setColor(0.7f, 0.9f, 1f, 0.10f + (i % 3) * 0.05f)
            if (dir == 1) {
                batch.draw(textures.fish, cx, cy, size, size * 0.5f)
            } else {
                batch.draw(textures.fish, cx + size, cy, -size, size * 0.5f)
            }
        }
    }
}
