package com.tubex.app.data.recommend

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderVarietyTest {
    @Test fun impressionPenalty_growsThenCaps() {
        assertEquals(0.0, Recommender.impressionPenalty(0), 1e-9)
        assertEquals(0.9, Recommender.impressionPenalty(1), 1e-9)
        assertEquals(2.5, Recommender.impressionPenalty(10), 1e-9)
        assertEquals(0.0, Recommender.impressionPenalty(-3), 1e-9)
    }

    @Test fun recencyBonus_prefersFreshAndPenalisesOld() {
        assertTrue(Recommender.recencyBonus("3 hours ago") > Recommender.recencyBonus("2 days ago"))
        assertTrue(Recommender.recencyBonus("2 days ago") > Recommender.recencyBonus("3 weeks ago"))
        assertTrue(Recommender.recencyBonus("2 years ago") < 0.0)
        assertEquals(0.0, Recommender.recencyBonus(null), 1e-9)
        assertEquals(0.0, Recommender.recencyBonus("something odd"), 1e-9)
    }

    @Test fun pickTopics_noRepeatsAndRespectsCount() {
        val w = listOf("a" to 5.0, "b" to 3.0, "c" to 1.0, "d" to 0.5)
        val r = Recommender.pickTopics(w, 3, Random(42))
        assertEquals(3, r.size)
        assertEquals(r.distinct(), r)
    }

    @Test fun pickTopics_ignoresNonPositiveAndHandlesSmallPool() {
        val r = Recommender.pickTopics(listOf("a" to 1.0, "b" to 0.0, "c" to -2.0), 5, Random(1))
        assertEquals(listOf("a"), r)
        assertTrue(Recommender.pickTopics(emptyList(), 3).isEmpty())
    }

    @Test fun pickTopics_variesAcrossSeeds() {
        val w = listOf("a" to 1.0, "b" to 1.0, "c" to 1.0, "d" to 1.0, "e" to 1.0, "f" to 1.0)
        val seen = (1..30).map { Recommender.pickTopics(w, 3, Random(it)).toSet() }.toSet()
        assertTrue(seen.size > 3)
    }
}
