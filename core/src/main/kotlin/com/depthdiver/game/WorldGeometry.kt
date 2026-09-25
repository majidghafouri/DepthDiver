package com.depthdiver.game

import kotlin.math.min
import kotlin.math.sqrt

internal const val WORLD_WIDTH_METERS = 40f
internal const val VIEW_HEIGHT_METERS = 30f
internal const val PLAYER_RADIUS_METERS = 0.9f
internal const val INITIAL_PLAYER_X_METERS = 20f
internal const val INITIAL_PLAYER_Y_METERS = -22.5f
internal const val PLAYER_BOTTOM_MARGIN_METERS = VIEW_HEIGHT_METERS * 0.25f

internal data class WorldCamera(val xMeters: Float, val yMeters: Float)

internal data class MoveDirection(val x: Float, val y: Float) {
    val length: Float get() = sqrt(x * x + y * y)

    val isZero: Boolean get() = x == 0f && y == 0f

    fun normalized(): MoveDirection {
        val len = length
        return if (len <= 0f) ZERO else MoveDirection(x / len, y / len)
    }

    companion object {
        val ZERO = MoveDirection(0f, 0f)
    }
}

internal data class WorldViewSpec(
    val screenWidth: Float,
    val screenHeight: Float,
    val playerRadiusMeters: Float = PLAYER_RADIUS_METERS,
) {
    val viewHeightMeters: Float = VIEW_HEIGHT_METERS

    val pixelsPerMeter: Float = screenHeight / VIEW_HEIGHT_METERS

    val metersPerPixel: Float = VIEW_HEIGHT_METERS / screenHeight

    val viewWidthMeters: Float = screenWidth * metersPerPixel

    val halfWidthMeters: Float = viewWidthMeters / 2f

    val halfHeightMeters: Float = viewHeightMeters / 2f

    init {
        require(screenWidth.isFinite() && screenWidth > 0f) { "screenWidth must be positive and finite" }
        require(screenHeight.isFinite() && screenHeight > 0f) { "screenHeight must be positive and finite" }
        require(playerRadiusMeters.isFinite() && playerRadiusMeters > 0f) { "playerRadiusMeters must be positive and finite" }
    }

    fun cameraXFor(playerXMeters: Float): Float {
        if (halfWidthMeters >= WORLD_WIDTH_METERS / 2f) return WORLD_WIDTH_METERS / 2f
        return playerXMeters.coerceIn(halfWidthMeters, WORLD_WIDTH_METERS - halfWidthMeters)
    }

    fun cameraYFor(playerYMeters: Float): Float {
        val surfaceBound = -halfHeightMeters
        val followBound = playerYMeters + halfHeightMeters - PLAYER_BOTTOM_MARGIN_METERS
        return min(surfaceBound, followBound)
    }

    fun cameraFor(playerXMeters: Float, playerYMeters: Float): WorldCamera =
        WorldCamera(cameraXFor(playerXMeters), cameraYFor(playerYMeters))

    fun worldLeft(camera: WorldCamera): Float = camera.xMeters - halfWidthMeters

    fun worldTop(camera: WorldCamera): Float = camera.yMeters + halfHeightMeters

    fun worldRight(camera: WorldCamera): Float = camera.xMeters + halfWidthMeters

    fun worldBottom(camera: WorldCamera): Float = camera.yMeters - halfHeightMeters

    fun worldXAt(screenX: Float, camera: WorldCamera): Float = worldLeft(camera) + screenX * metersPerPixel

    fun worldYAt(screenYFromTop: Float, camera: WorldCamera): Float = worldTop(camera) - screenYFromTop * metersPerPixel

    fun screenXAt(worldX: Float, camera: WorldCamera): Float = (worldX - worldLeft(camera)) / metersPerPixel

    fun screenYAt(worldY: Float, camera: WorldCamera): Float = (worldTop(camera) - worldY) / metersPerPixel

    fun touchDirection(
        playerXMeters: Float,
        playerYMeters: Float,
        screenX: Float,
        screenYFromTop: Float,
        camera: WorldCamera,
    ): MoveDirection {
        val toX = worldXAt(screenX, camera) - playerXMeters
        val toY = worldYAt(screenYFromTop, camera) - playerYMeters
        val dist = sqrt(toX * toX + toY * toY)
        return if (dist > playerRadiusMeters) MoveDirection(toX / dist, toY / dist) else MoveDirection.ZERO
    }
}
