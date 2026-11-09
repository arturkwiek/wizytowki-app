package com.wizytowki.scan

import com.wizytowki.scan.ScanSession.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionTest {

    @Test
    fun newSession_waitsForFront() {
        assertEquals(Step.FRONT, ScanSession().step)
    }

    @Test
    fun frontThenBack_isDone() {
        val session = ScanSession().withCapture("front").withCapture("back")
        assertEquals(Step.DONE, session.step)
        assertEquals("front", session.frontUri)
        assertEquals("back", session.backUri)
    }

    @Test
    fun afterFront_asksForBack() {
        assertEquals(Step.BACK, ScanSession().withCapture("front").step)
    }

    @Test
    fun skipBack_finishesWithoutBack() {
        val session = ScanSession().withCapture("front").skipBack()
        assertEquals(Step.DONE, session.step)
        assertTrue(session.backSkipped)
        assertNull(session.backUri)
    }

    @Test
    fun skipBack_beforeFront_isIgnored() {
        val session = ScanSession().skipBack()
        assertEquals(Step.FRONT, session.step)
        assertFalse(session.backSkipped)
    }

    @Test
    fun retakeFront_keepsBack() {
        val session = ScanSession().withCapture("front").withCapture("back").retakeFront()
        assertEquals(Step.FRONT, session.step)
        assertEquals("back", session.backUri)

        val retaken = session.withCapture("front2")
        assertEquals(Step.DONE, retaken.step)
        assertEquals("front2", retaken.frontUri)
        assertEquals("back", retaken.backUri)
    }

    @Test
    fun retakeBack_keepsFrontAndClearsSkip() {
        val session = ScanSession().withCapture("front").skipBack().retakeBack()
        assertEquals(Step.BACK, session.step)
        assertEquals("front", session.frontUri)
        assertFalse(session.backSkipped)
    }

    @Test
    fun captureWhenDone_isIgnored() {
        val session = ScanSession().withCapture("front").withCapture("back")
        assertEquals(session, session.withCapture("extra"))
    }

    @Test
    fun polishCharactersInUri_arePreserved() {
        val session = ScanSession().withCapture("file:///łódź/żółć.jpg")
        assertEquals("file:///łódź/żółć.jpg", session.frontUri)
    }

    @Test
    fun inSampleSize_keepsAtLeastMaxSize() {
        assertEquals(1, calculateInSampleSize(600, 400, 600))
        assertEquals(1, calculateInSampleSize(1100, 1100, 600))
        assertEquals(2, calculateInSampleSize(1200, 1200, 600))
        assertEquals(4, calculateInSampleSize(4000, 3000, 600))
        assertEquals(1, calculateInSampleSize(100, 100, 600))
    }
}
