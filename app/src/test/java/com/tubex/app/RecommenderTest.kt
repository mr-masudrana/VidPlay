package com.tubex.app

import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecommenderTest {
    private val original = TimeZone.getDefault()

    @Before fun setUtc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    @After fun restore() = TimeZone.setDefault(original)

    private fun cal(tz: String, y: Int, m: Int, d: Int, h: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone(tz)).apply { clear(); set(y, m, d, h, 0, 0) }

    @Test fun keywords_dropStopWordsDigitsAndShortWords() {
        assertEquals(listOf("cricket", "highlights"), Recommender.keywords("The BEST Cricket Highlights 2024 of"))
    }

    @Test fun keywords_areDistinctAndCappedAtEight() {
        val r = Recommender.keywords("alpha alpha beta gamma delta epsilon zeta theta iota kappa lambda")
        assertEquals(8, r.size)
        assertEquals(r.distinct(), r)
    }

    @Test fun bucket_weekdayMorning() {
        assertEquals("wd-m", Recommender.bucket(cal("UTC", 2026, Calendar.OCTOBER, 28, 9)))   // বুধবার
    }

    @Test fun bucket_saturdayEveningIsWeekend() {
        assertEquals("we-e", Recommender.bucket(cal("UTC", 2026, Calendar.OCTOBER, 31, 20)))
    }

    @Test fun bucket_dhakaWeekendIsFridayAndSaturday() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
        assertEquals("we-n", Recommender.bucket(cal("Asia/Dhaka", 2026, Calendar.OCTOBER, 30, 23)))  // শুক্রবার রাত
        assertEquals("wd-n", Recommender.bucket(cal("Asia/Dhaka", 2026, Calendar.NOVEMBER, 1, 23)))  // রবিবার রাত
    }
}

class BlocksTest {
    @Test fun emptyBlocklistAllowsEverything() {
        assertTrue(Blocks.allowed(emptySet(), "any title", "Any", "u"))
    }

    @Test fun blocksByChannelCaseInsensitive() {
        assertFalse(Blocks.allowed(setOf("ch:spamchannel"), "t", "SpamChannel", "u"))
    }

    @Test fun blocksByVideoAndKeyword() {
        assertFalse(Blocks.allowed(setOf("vid:u1"), "t", "c", "u1"))
        assertFalse(Blocks.allowed(setOf("kw:prank"), "Funny PRANK compilation", "c", "u2"))
        assertTrue(Blocks.allowed(setOf("kw:prank"), "Cooking show", "c", "u3"))
    }
}
