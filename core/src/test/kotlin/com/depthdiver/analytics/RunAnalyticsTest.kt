package com.depthdiver.analytics

import com.depthdiver.TestPreferences
import com.depthdiver.run.RunTerminalReason
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunAnalyticsTest {

    @BeforeTest
    fun setUp() = Unit

    @AfterTest
    fun tearDown() {
        AnalyticsReport.useOverride(null)
    }

    private fun sample(
        depth: Float,
        seconds: Float = 30f,
        outcome: RunOutcome = RunOutcome.OXYGEN,
        mutations: List<String> = emptyList(),
    ) = RunSample(depth, score = (depth * 10).toInt(), seconds, outcome, difficulty = 1, mutations = mutations)

    @Test
    fun anEmptyTrackerSummarisesToNothing() {
        val s = RunAnalytics().summary()
        assertEquals(0, s.runs)
        assertEquals(0f, s.bestDepth)
    }

    @Test
    fun theMedianIsTheTypicalRunNotTheBestOne() {
        // The whole point is that a single 900m run must not make the game look
        // like it works; the middle run is the one most players had.
        val a = RunAnalytics()
        listOf(10f, 20f, 30f, 40f, 900f).forEach { a.record(sample(it)) }
        assertEquals(30f, a.summary().medianDepth)
        assertEquals(900f, a.summary().bestDepth)
    }

    @Test
    fun theMedianOfAnEvenCountAveragesTheMiddleTwo() {
        val a = RunAnalytics()
        listOf(10f, 20f, 30f, 40f).forEach { a.record(sample(it)) }
        assertEquals(25f, a.summary().medianDepth)
    }

    @Test
    fun theTypicalStopIsReportedToTheNearestTenMetres() {
        val a = RunAnalytics()
        listOf(44f, 47f, 46f, 43f).forEach { a.record(sample(it)) }
        assertEquals(50, a.summary().typicalStopMeters)
    }

    @Test
    fun theDepthFunnelIsMonotonic() {
        // If more players reach 300m than reach 50m, the numbers are wrong and
        // the whole report is worse than no report.
        val a = RunAnalytics()
        listOf(20f, 60f, 120f, 350f, 400f).forEach { a.record(sample(it)) }
        val reached = a.summary().reached
        var previous = 1.0
        for (mark in DEPTH_MARKS) {
            val fraction = reached.getValue(mark)
            assertTrue(fraction <= previous + 1e-9, "reach at $mark rose from $previous to $fraction")
            previous = fraction
        }
        assertEquals(0.8, reached.getValue(50), 1e-6)
        assertEquals(0.6, reached.getValue(100), 1e-6)
        assertEquals(0.0, reached.getValue(500), 1e-6)
    }

    @Test
    fun deathsAreCountedByCause() {
        val a = RunAnalytics()
        a.record(sample(30f, outcome = RunOutcome.OXYGEN))
        a.record(sample(30f, outcome = RunOutcome.HAZARD))
        a.record(sample(300f, outcome = RunOutcome.COMPLETED))
        val s = a.summary()
        assertEquals(3, s.runs)
        assertEquals(1, s.deathsByOxygen)
        assertEquals(1, s.deathsByHazard)
        assertEquals(1, s.completions)
    }

    @Test
    fun everyTerminalReasonMapsToAnOutcome() {
        // RunTerminalReason has grown cases; an unmapped one must not crash the
        // end of a run.
        RunTerminalReason.values().forEach {
            assertTrue(RunOutcome.of(it).name.isNotEmpty())
        }
        assertEquals(RunOutcome.ABANDONED, RunOutcome.of(null))
    }

    @Test
    fun theHistoryIsBounded() {
        // A tracker that grows forever is a memory leak with a dashboard on it.
        val a = RunAnalytics(capacity = 10)
        repeat(50) { a.record(sample(it.toFloat())) }
        assertEquals(10, a.size())
        assertTrue(a.all().all { it.depthMeters >= 40f }, "the oldest samples should have been dropped")
    }

    @Test
    fun mutationPopularityIsTakenOverOffered() {
        val a = RunAnalytics()
        val taken = mapOf("greedy" to 8, "ghostly" to 1)
        val offered = mapOf("greedy" to 10, "ghostly" to 10)
        val popularity = a.mutationPopularity(taken, offered)
        assertEquals(0.8, popularity.getValue("greedy"), 1e-6)
        assertEquals(0.1, popularity.getValue("ghostly"), 1e-6)
    }

    @Test
    fun aMutationThatWasNeverOfferedHasNoPopularity() {
        val popularity = RunAnalytics().mutationPopularity(mapOf("x" to 3), emptyMap())
        assertEquals(0.0, popularity.getValue("x"), 1e-6)
    }

    @Test
    fun theDepthHistogramBucketsRuns() {
        val a = RunAnalytics()
        listOf(10f, 20f, 55f, 70f, 200f).forEach { a.record(sample(it)) }
        val histogram = a.depthHistogram(bucketMeters = 25)
        // 10 and 20 share the 0 bucket; 55 and 70 share the 50 bucket.
        assertEquals(2, histogram[0])
        assertEquals(2, histogram[50])
        assertEquals(1, histogram[200])
        // The buckets must tile the range with no gaps, or a run would vanish.
        assertEquals(listOf(0, 50, 200), histogram.keys.toList())
    }

    // --- the persisted report ------------------------------------------------

    @Test
    fun thePersistedReportSurvivesWithoutAHistory() {
        val prefs = TestPreferences("analytics-test")
        AnalyticsReport.useOverride(prefs)
        AnalyticsReport.reset()

        assertTrue(AnalyticsReport.render().contains("No runs recorded"))

        repeat(3) { AnalyticsReport.record(sample(40f + it * 10, outcome = RunOutcome.HAZARD, mutations = listOf("greedy"))) }

        assertEquals(3, AnalyticsReport.runs())
        assertEquals(60f, AnalyticsReport.bestDepth(), 1f)
        assertEquals(mapOf("greedy" to 3), AnalyticsReport.mutationCounts())
    }

    @Test
    fun thePersistedReportRendersSomethingReadable() {
        val prefs = TestPreferences("analytics-test")
        AnalyticsReport.useOverride(prefs)
        AnalyticsReport.reset()
        AnalyticsReport.record(sample(120f, outcome = RunOutcome.OXYGEN, mutations = listOf("ironLungs")))

        val text = AnalyticsReport.render()
        listOf("runs", "average depth", "best depth", "oxygen", "ironLungs").forEach {
            assertTrue(text.contains(it), "report should mention '$it':\n$text")
        }
    }

    @Test
    fun thePersistedReportDoesNotInventAFunnel() {
        // A sum and a count cannot recover "how many passed 100m". Returning an
        // empty map says so; returning zeros would look like a real measurement.
        val prefs = TestPreferences("analytics-test")
        AnalyticsReport.useOverride(prefs)
        AnalyticsReport.reset()
        AnalyticsReport.record(sample(200f))
        assertTrue(AnalyticsReport.summary().reached.isEmpty())
    }

    @Test
    fun mutationCountsCannotGrowWithoutBound() {
        val prefs = TestPreferences("analytics-test")
        AnalyticsReport.useOverride(prefs)
        AnalyticsReport.reset()
        repeat(40) { AnalyticsReport.record(sample(50f, mutations = listOf("m$it"))) }
        // One key per distinct mutation id, not one per run.
        assertEquals(40, AnalyticsReport.mutationCounts().size)
    }

    @Test
    fun anUnreadableReasonStillEndsTheRunSafely() {
        // A null reason must not throw at the end of a run, and must be counted
        // as the player stopping rather than as a completed dive.
        assertEquals(RunOutcome.ABANDONED, RunOutcome.of(null))
    }
}
