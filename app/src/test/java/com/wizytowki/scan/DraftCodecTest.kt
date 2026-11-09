package com.wizytowki.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftCodecTest {

    @Test
    fun roundTrip_bothSides() {
        val session = ScanSession(frontUri = "file:///data/scan_draft/front_1.jpg", backUri = "file:///data/scan_draft/back_2.jpg")
        assertEquals(session, DraftCodec.decode(DraftCodec.encode(session)))
    }

    @Test
    fun roundTrip_skippedBack() {
        val session = ScanSession(frontUri = "file:///f.jpg", backSkipped = true)
        assertEquals(session, DraftCodec.decode(DraftCodec.encode(session)))
    }

    @Test
    fun roundTrip_onlyBack_afterRetakeFront() {
        val session = ScanSession(backUri = "file:///b.jpg")
        assertEquals(session, DraftCodec.decode(DraftCodec.encode(session)))
    }

    @Test
    fun roundTrip_polishCharactersAndEqualsSign() {
        val session = ScanSession(frontUri = "file:///łódź/żółć=1.jpg")
        assertEquals(session, DraftCodec.decode(DraftCodec.encode(session)))
    }

    @Test
    fun emptySession_roundTrips_andHasNoContent() {
        val decoded = DraftCodec.decode(DraftCodec.encode(ScanSession()))
        assertEquals(ScanSession(), decoded)
        assertFalse(decoded!!.hasContent)
    }

    @Test
    fun unknownVersion_isRejected() {
        assertNull(DraftCodec.decode("version=99\nfront=file:///f.jpg\nback=\nbackSkipped=false\n"))
    }

    @Test
    fun corruptText_isRejected() {
        assertNull(DraftCodec.decode(""))
        assertNull(DraftCodec.decode("garbage"))
        assertNull(DraftCodec.decode("version=1\nfront=file:///f.jpg\nback=\nbackSkipped=maybe\n"))
    }

    @Test
    fun skippedWithBackPhoto_isNormalized() {
        val decoded = DraftCodec.decode("version=1\nfront=file:///f.jpg\nback=file:///b.jpg\nbackSkipped=true\n")
        assertEquals(ScanSession(frontUri = "file:///f.jpg", backUri = "file:///b.jpg", backSkipped = false), decoded)
    }

    @Test
    fun hasContent_whenAnyPhoto() {
        assertTrue(ScanSession(frontUri = "f").hasContent)
        assertTrue(ScanSession(backUri = "b").hasContent)
    }
}
