package com.depthdiver.world

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.depthdiver.entity.Hazard
import com.depthdiver.game.Biome
import com.depthdiver.game.WaterColor
import com.depthdiver.game.WORLD_WIDTH_METERS
import com.depthdiver.game.WorldCamera
import com.depthdiver.game.WorldViewSpec
import com.depthdiver.entity.Pickup

/** A particle reduced to what drawing needs. */
data class ParticleView(
    val x: Float,
    val y: Float,
    val life: Float,
    val maxLife: Float,
    val color: Color,
    val size: Float,
)

/**
 * Screen shake in progress, or null when the world should be drawn steady.
 *
 * Null rather than a boolean + zeros so the renderer never has to know that
 * shake only applies while a run is actually being played.
 */
data class WorldShake(val timer: Float, val duration: Float, val intensity: Float)

/**
 * Everything the world renderer reads, for one frame.
 *
 * Built by the game because the game owns the simulation. The renderer gets a
 * snapshot so that "what the world looks like" is answerable without a
 * simulation, which is what makes it testable.
 */
data class WorldViewState(
    val spec: WorldViewSpec,
    val camera: WorldCamera,
    val water: WaterColor,
    val depth: Float,
    val elapsed: Float,
    val shake: WorldShake?,
    val particles: List<ParticleView>,
    val hazards: List<Hazard>,
    val pickups: List<Pickup>,
    val playerX: Float,
    val playerY: Float,
    val playerRadius: Float,
)

/**
 * The textures the world is drawn from, grouped so the renderer takes one
 * dependency instead of twelve.
 */
class WorldTextures(
    val pixel: Texture,
    val player: Texture,
    val rock: Texture,
    val mine: Texture,
    val jellyfish: Texture,
    val shark: Texture,
    val eel: Texture,
    val angler: Texture,
    val vortex: Texture,
    val oxygen: Texture,
    val pearl: Texture,
    val fish: Texture,
)

/**
 * The water colour at a depth: the biome's own colour blended toward the next
 * biome as the run approaches its lower bound.
 *
 * A pure function of depth so the blend can be tested without a run in progress.
 */
fun waterColorAt(depth: Float): WaterColor {
    val current = Biome.forDepth(depth)
    return current.blendTo(Biome.nextOf(current), Biome.progressWithin(depth, current))
}

/** The surface-relative depth at which the light rays stop being drawn. */
internal fun surfaceFactor(depth: Float): Float =
    (1f - (depth / WORLD_WIDTH_METERS)).coerceIn(0f, 1f)

/** True while a ray shaft should be drawn at all. */
internal fun raysVisible(depth: Float): Boolean = surfaceFactor(depth) > 0.05f
