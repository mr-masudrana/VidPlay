package com.tubex.app.data.backup

import com.tubex.app.data.local.entity.InterestEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileBackupTest {
    @Test fun isValid_acceptsTubexAndLegacyMarker() {
        assertTrue(ProfileBackup.isValid(JSONObject().put("app", "tubex")))
        assertTrue(ProfileBackup.isValid(JSONObject().put("app", "vidplay")))
        assertFalse(ProfileBackup.isValid(JSONObject().put("app", "other")))
    }

    @Test fun interests_roundTrip() {
        val src = listOf(InterestEntity("cricket", 4.5, 1_000L), InterestEntity("music", 1.25, 2_000L))
        val back = ProfileBackup.interestsFromJson(JSONObject(ProfileBackup.interestsToJson(src).toString()))
        assertEquals(src, back)
    }

    @Test fun interestsFromJson_rejectsForeignOrEmptyFiles() {
        assertNull(ProfileBackup.interestsFromJson(JSONObject().put("app", "other").put("interests", org.json.JSONArray())))
        assertNull(ProfileBackup.interestsFromJson(JSONObject().put("app", "tubex")))
    }
}
