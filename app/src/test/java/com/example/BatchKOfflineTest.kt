package com.example

import com.example.domain.activity.AttendanceFlagDecision
import com.example.domain.activity.AttendanceMaintenancePolicy
import com.example.domain.attendance.AttendanceRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchKOfflineTest {

    private val algiersZone: ZoneId = ZoneId.of("Africa/Algiers")

    @Test
    fun `defaultScoutingYearStart returns Sept 1 of previous year on Aug 31`() {
        val aug31End = ZonedDateTime.of(2026, 8, 31, 23, 59, 59, 0, algiersZone)
            .toInstant()
            .toEpochMilli()
        val expected = ZonedDateTime.of(2025, 9, 1, 0, 0, 0, 0, algiersZone)
            .toInstant()
            .toEpochMilli()

        val actual = AttendanceRepository.defaultScoutingYearStart(nowMillis = aug31End, zoneId = algiersZone)
        assertEquals(expected, actual)
    }

    @Test
    fun `defaultScoutingYearStart returns Sept 1 of same year at Sept 1 00-00 Algiers`() {
        val sept1Midnight = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, algiersZone)
            .toInstant()
            .toEpochMilli()

        val actual = AttendanceRepository.defaultScoutingYearStart(nowMillis = sept1Midnight, zoneId = algiersZone)
        assertEquals(sept1Midnight, actual)
    }

    @Test
    fun `defaultScoutingYearStart returns Sept 1 of same year on Dec 15`() {
        val dec15 = ZonedDateTime.of(2026, 12, 15, 14, 30, 0, 0, algiersZone)
            .toInstant()
            .toEpochMilli()
        val expected = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, algiersZone)
            .toInstant()
            .toEpochMilli()

        val actual = AttendanceRepository.defaultScoutingYearStart(nowMillis = dec15, zoneId = algiersZone)
        assertEquals(expected, actual)
    }

    @Test
    fun `shouldUpdateAttendanceActivityDate handles missing, zero, invalid, equal, and differing dates`() {
        val startDate = 1_757_000_000_000L

        assertTrue(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(null, startDate))
        assertTrue(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(0L, startDate))
        assertTrue(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(-100L, startDate))
        assertTrue(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(startDate - 86_400_000L, startDate))

        assertFalse(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(startDate, startDate))
        assertFalse(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(null, 0L))
        assertFalse(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(0L, -1L))
        assertFalse(AttendanceMaintenancePolicy.shouldUpdateAttendanceActivityDate(1234L, 0L))
    }

    @Test
    fun `decideAttendanceFlagUpdate never writes on unknown server state and updates only mismatches`() {
        assertEquals(
            AttendanceFlagDecision.UnknownSkip,
            AttendanceMaintenancePolicy.decideAttendanceFlagUpdate(currentFlag = false, hasAttendanceOnServer = null)
        )
        assertEquals(
            AttendanceFlagDecision.UnknownSkip,
            AttendanceMaintenancePolicy.decideAttendanceFlagUpdate(currentFlag = true, hasAttendanceOnServer = null)
        )

        assertEquals(
            AttendanceFlagDecision.Update(true),
            AttendanceMaintenancePolicy.decideAttendanceFlagUpdate(currentFlag = false, hasAttendanceOnServer = true)
        )
        assertEquals(
            AttendanceFlagDecision.Update(false),
            AttendanceMaintenancePolicy.decideAttendanceFlagUpdate(currentFlag = true, hasAttendanceOnServer = false)
        )

        assertEquals(
            AttendanceFlagDecision.NoChange,
            AttendanceMaintenancePolicy.decideAttendanceFlagUpdate(currentFlag = true, hasAttendanceOnServer = true)
        )
        assertEquals(
            AttendanceFlagDecision.NoChange,
            AttendanceMaintenancePolicy.decideAttendanceFlagUpdate(currentFlag = false, hasAttendanceOnServer = false)
        )
    }
}
