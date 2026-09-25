package com.depthdiver.game

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldGeometryTest {

    private val landscape = WorldViewSpec(800f, 600f)
    private val portrait = WorldViewSpec(480f, 800f)

    @Test
    fun theWorldConstantsMatchTheDesign() {
        assertEquals(40f, WORLD_WIDTH_METERS)
        assertEquals(30f, VIEW_HEIGHT_METERS)
        assertEquals(0.9f, PLAYER_RADIUS_METERS)
        assertEquals(20f, INITIAL_PLAYER_X_METERS)
        assertEquals(-22.5f, INITIAL_PLAYER_Y_METERS)
    }

    @Test
    fun aSpecNeedsPositiveFiniteScreenSizeAndRadius() {
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(0f, 600f) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(-800f, 600f) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(800f, 0f) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(800f, -600f) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(Float.NaN, 600f) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(800f, Float.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(800f, 600f, 0f) }
        assertFailsWith<IllegalArgumentException> { WorldViewSpec(800f, 600f, Float.NaN) }
    }

    @Test
    fun fourByThreeLandscapeShowsTheWholeFortyMeterWorld() {
        assertEquals(30f, landscape.viewHeightMeters, 1e-4f)
        assertEquals(40f, landscape.viewWidthMeters, 1e-3f)
        assertEquals(20f, landscape.pixelsPerMeter, 1e-4f)
        assertEquals(0.05f, landscape.metersPerPixel, 1e-6f)

        val camera = landscape.cameraFor(INITIAL_PLAYER_X_METERS, INITIAL_PLAYER_Y_METERS)
        assertEquals(WORLD_WIDTH_METERS / 2f, camera.xMeters, 1e-4f)
        assertEquals(-15f, camera.yMeters, 1e-4f)
        assertEquals(0f, landscape.worldLeft(camera), 1e-4f)
        assertEquals(WORLD_WIDTH_METERS, landscape.worldRight(camera), 1e-3f)
        assertEquals(0f, landscape.worldTop(camera), 1e-4f)
        assertEquals(-30f, landscape.worldBottom(camera), 1e-4f)

        assertEquals(400f, landscape.screenXAt(INITIAL_PLAYER_X_METERS, camera), 1e-3f)
        assertEquals(450f, landscape.screenYAt(INITIAL_PLAYER_Y_METERS, camera), 1e-2f)
    }

    @Test
    fun theCameraOnlyClampsXWhenTheWorldIsWiderThanTheView() {
        for (playerX in listOf(0f, 1f, 20f, 39f, 40f, 500f, -500f)) {
            assertEquals(20f, landscape.cameraXFor(playerX), 1e-4f, "playerX $playerX")
        }

        val ultrawide = WorldViewSpec(1600f, 600f)
        assertTrue(ultrawide.viewWidthMeters > WORLD_WIDTH_METERS)
        assertEquals(20f, ultrawide.cameraXFor(0f), 1e-4f)
        assertEquals(20f, ultrawide.cameraXFor(40f), 1e-4f)
    }

    @Test
    fun aNarrowPortraitViewFollowsThePlayerAcrossTheWorld() {
        assertEquals(18f, portrait.viewWidthMeters, 1e-3f)

        assertEquals(9f, portrait.cameraXFor(2f), 1e-4f)
        assertEquals(9f, portrait.cameraXFor(0f), 1e-4f)
        assertEquals(20f, portrait.cameraXFor(20f), 1e-4f)
        assertEquals(31f, portrait.cameraXFor(38f), 1e-4f)
        assertEquals(31f, portrait.cameraXFor(40f), 1e-4f)
        assertEquals(31f, portrait.cameraXFor(999f), 1e-4f)

        for (playerX in listOf(0f, 0.5f, 12.5f, 27.5f, 40f, -50f, 100f)) {
            val camera = portrait.cameraFor(playerX, INITIAL_PLAYER_Y_METERS)
            assertTrue(portrait.worldLeft(camera) >= 0f, "playerX $playerX")
            assertTrue(portrait.worldRight(camera) <= WORLD_WIDTH_METERS, "playerX $playerX")
        }
    }

    @Test
    fun theSurfaceStaysPinnedToTheTopOfTheViewAtTheStart() {
        for (screen in listOf(WorldViewSpec(800f, 600f), WorldViewSpec(480f, 800f), WorldViewSpec(1080f, 1920f))) {
            for (playerX in listOf(0f, 20f, 40f)) {
                val camera = screen.cameraFor(playerX, INITIAL_PLAYER_Y_METERS)
                assertEquals(0f, screen.worldTop(camera), 1e-4f, "x $playerX")
                assertEquals(-15f, camera.yMeters, 1e-4f, "x $playerX")
            }
            val shallow = screen.cameraFor(INITIAL_PLAYER_X_METERS, 0f)
            assertEquals(0f, screen.worldTop(shallow), 1e-4f)
        }
    }

    @Test
    fun thePlayerKeepsTheSameLowerMarginAtEveryDepth() {
        for (depth in listOf(22.5f, 50f, 300f, 1000f, 10_000f)) {
            val playerY = -depth
            val camera = landscape.cameraFor(INITIAL_PLAYER_X_METERS, playerY)
            assertEquals(450f, landscape.screenYAt(playerY, camera), 1e-1f, "depth $depth")
            assertEquals(600f, landscape.screenYAt(landscape.worldBottom(camera), camera), 1e-1f, "depth $depth")
            assertTrue(landscape.worldTop(camera) <= 0f, "depth $depth")
            assertTrue(landscape.worldTop(camera).isFinite(), "depth $depth")
            assertTrue(camera.yMeters.isFinite(), "depth $depth")
            assertTrue(playerY in landscape.worldBottom(camera)..landscape.worldTop(camera), "depth $depth")
        }
    }

    @Test
    fun aShallowPlayerStaysOnScreenUnderThePinnedSurface() {
        for (depth in listOf(0f, 1f, 10f, 22.5f)) {
            val camera = landscape.cameraFor(INITIAL_PLAYER_X_METERS, -depth)
            assertEquals(0f, landscape.worldTop(camera), 1e-4f, "depth $depth")
            val screenY = landscape.screenYAt(-depth, camera)
            assertTrue(screenY in 0f..600f, "depth $depth screenY $screenY")
        }
    }

    @Test
    fun worldConversionsRoundTripAtAnyDepth() {
        for (spec in listOf(landscape, portrait, WorldViewSpec(1080f, 1920f))) {
            for (worldY in listOf(0f, -1f, -22.5f, -300f, -1500f)) {
                val camera = spec.cameraFor(INITIAL_PLAYER_X_METERS, worldY)
                for (worldX in listOf(0f, 1f, 20f, 40f)) {
                    val screenX = spec.screenXAt(worldX, camera)
                    val screenY = spec.screenYAt(worldY, camera)
                    assertEquals(worldX, spec.worldXAt(screenX, camera), 1e-3f, "y $worldY")
                    assertEquals(worldY, spec.worldYAt(screenY, camera), 1e-3f, "y $worldY")
                }
            }
        }
    }

    @Test
    fun screenYIsMeasuredDownFromTheTopOfTheView() {
        val camera = landscape.cameraFor(INITIAL_PLAYER_X_METERS, INITIAL_PLAYER_Y_METERS)
        assertEquals(0f, landscape.worldYAt(0f, camera), 1e-3f)
        assertEquals(-30f, landscape.worldYAt(600f, camera), 1e-3f)
        assertEquals(0f, landscape.worldXAt(0f, camera), 1e-3f)
        assertEquals(40f, landscape.worldXAt(800f, camera), 1e-2f)

        val deeper = landscape.worldYAt(450f, camera)
        val shallower = landscape.worldYAt(350f, camera)
        assertTrue(deeper < shallower, "$deeper should be below $shallower")
    }

    @Test
    fun resizingOnlyChangesTheViewNotTheWorld() {
        val phone = WorldViewSpec(1080f, 1920f)
        assertEquals(30f, phone.viewHeightMeters, 1e-4f)
        assertEquals(16.875f, phone.viewWidthMeters, 1e-2f)
        assertNotEquals(landscape.viewWidthMeters, phone.viewWidthMeters)

        for (spec in listOf(landscape, phone)) {
            val camera = spec.cameraFor(INITIAL_PLAYER_X_METERS, INITIAL_PLAYER_Y_METERS)
            assertEquals(-15f, camera.yMeters, 1e-4f)
            val screenX = spec.screenXAt(INITIAL_PLAYER_X_METERS, camera)
            val screenY = spec.screenYAt(INITIAL_PLAYER_Y_METERS, camera)
            assertEquals(INITIAL_PLAYER_X_METERS, spec.worldXAt(screenX, camera), 1e-3f)
            assertEquals(INITIAL_PLAYER_Y_METERS, spec.worldYAt(screenY, camera), 1e-3f)
        }

        assertEquals(20f, landscape.cameraXFor(2f), 1e-4f)
        assertTrue(phone.cameraXFor(2f) < 20f)
    }

    @Test
    fun touchDirectionPointsFromThePlayerTowardTheTouch() {
        val camera = landscape.cameraFor(INITIAL_PLAYER_X_METERS, INITIAL_PLAYER_Y_METERS)
        val playerX = INITIAL_PLAYER_X_METERS
        val playerY = INITIAL_PLAYER_Y_METERS

        assertTrue(directionAt(playerX, playerY, 400f, 450f, camera).isZero)
        assertTrue(directionAt(playerX, playerY, 410f, 450f, camera).isZero)

        assertEquals(MoveDirection(1f, 0f), directionAt(playerX, playerY, 420f, 450f, camera))
        assertEquals(MoveDirection(-1f, 0f), directionAt(playerX, playerY, 380f, 450f, camera))
        assertEquals(MoveDirection(0f, 1f), directionAt(playerX, playerY, 400f, 350f, camera))
        assertEquals(MoveDirection(0f, -1f), directionAt(playerX, playerY, 400f, 550f, camera))

        val diagonal = directionAt(playerX, playerY, 500f, 350f, camera)
        assertEquals(sqrt(0.5f), diagonal.x, 1e-4f)
        assertEquals(sqrt(0.5f), diagonal.y, 1e-4f)
        assertEquals(1f, diagonal.length, 1e-4f)
    }

    @Test
    fun aMoveDirectionNormalizesAndStaysFinite() {
        assertTrue(MoveDirection.ZERO.isZero)
        assertEquals(MoveDirection.ZERO, MoveDirection(0f, 0f).normalized())
        assertEquals(MoveDirection(0.6f, 0.8f), MoveDirection(3f, 4f).normalized())
        assertEquals(5f, MoveDirection(3f, 4f).length, 1e-5f)
    }

    @Test
    fun everyDerivedValueStaysFinite() {
        val screens = listOf(
            WorldViewSpec(1f, 1f),
            landscape,
            WorldViewSpec(1080f, 1920f),
            WorldViewSpec(100_000f, 3f),
            WorldViewSpec(3f, 100_000f),
        )
        val positions = listOf(
            0f to 0f,
            INITIAL_PLAYER_X_METERS to INITIAL_PLAYER_Y_METERS,
            1f to -300f,
            40f to -10_000f,
            -1_000f to -1_000f,
        )

        for (spec in screens) {
            assertTrue(spec.viewWidthMeters.isFinite())
            assertTrue(spec.viewHeightMeters.isFinite())
            assertTrue(spec.pixelsPerMeter.isFinite())
            assertTrue(spec.metersPerPixel.isFinite())
            for ((playerX, playerY) in positions) {
                val camera = spec.cameraFor(playerX, playerY)
                assertTrue(camera.xMeters.isFinite(), "$playerX $playerY")
                assertTrue(camera.yMeters.isFinite(), "$playerX $playerY")
                assertTrue(spec.worldTop(camera).isFinite(), "$playerX $playerY")
                assertTrue(spec.worldBottom(camera).isFinite(), "$playerX $playerY")
                for (screen in listOf(0f, spec.screenWidth / 2f, spec.screenWidth, spec.screenHeight)) {
                    assertTrue(spec.worldXAt(screen, camera).isFinite(), "$playerX $playerY")
                    assertTrue(spec.worldYAt(screen, camera).isFinite(), "$playerX $playerY")
                    assertTrue(
                        spec.touchDirection(playerX, playerY, screen, screen, camera).length.isFinite(),
                        "$playerX $playerY",
                    )
                }
                assertFalse(spec.worldTop(camera) > 0f, "$playerX $playerY")
            }
        }
    }

    private fun directionAt(
        playerX: Float,
        playerY: Float,
        screenX: Float,
        screenY: Float,
        camera: WorldCamera,
    ): MoveDirection = landscape.touchDirection(playerX, playerY, screenX, screenY, camera)
}
