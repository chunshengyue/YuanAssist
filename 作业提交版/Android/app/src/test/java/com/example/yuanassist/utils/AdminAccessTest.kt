package com.example.yuanassist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAccessTest {

    @Test
    fun admin_device_id_passes() {
        assertTrue(isFeedbackAdminDevice(FEEDBACK_ADMIN_DEVICE_ID))
    }

    @Test
    fun non_admin_device_id_fails() {
        assertFalse(isFeedbackAdminDevice("not-admin-device"))
    }

    @Test
    fun first_feedback_image_url_picks_first_non_blank_value() {
        val result = firstFeedbackImageUrl(
            """
            
              https://example.com/first.png
              
              https://example.com/second.png
            """.trimIndent(),
        )

        assertEquals("https://example.com/first.png", result)
    }
}
