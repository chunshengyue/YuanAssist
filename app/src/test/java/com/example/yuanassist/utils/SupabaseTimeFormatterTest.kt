package com.example.yuanassist.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTimeFormatterTest {

    @Test
    fun `formats legacy utc text as beijing time`() {
        assertEquals(
            "2026-05-04 14:32",
            SupabaseTimeFormatter.formatToBeijing("2026-05-04 06:32:14")
        )
    }

    @Test
    fun `formats postgres utc offset text as beijing time`() {
        assertEquals(
            "2026-05-04 12:41",
            SupabaseTimeFormatter.formatToBeijing("2026-05-04 04:41:28+00")
        )
    }

    @Test
    fun `formats iso zulu text as beijing time`() {
        assertEquals(
            "2026-05-04 14:32",
            SupabaseTimeFormatter.formatToBeijing("2026-05-04T06:32:14.000Z")
        )
    }

    @Test
    fun `parses timestamp for supported supabase formats`() {
        assertTrue(SupabaseTimeFormatter.parseTimestamp("2026-05-04 06:32:14+00") > 0L)
        assertTrue(SupabaseTimeFormatter.parseTimestamp("2026-05-04T06:32:14Z") > 0L)
        assertTrue(SupabaseTimeFormatter.parseTimestamp("2026-05-04T06:32:14.123456Z") > 0L)
    }
}
