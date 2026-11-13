package com.wizytowki.ui.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Scan date, event and optional place (WF-24). */
@RunWith(AndroidJUnit4::class)
class MeetingSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun eventIsTypedAndPlaceExplainedBeforeAsking() {
        var event by mutableStateOf("")
        compose.setContent { MeetingSection(event, "", enabled = true, onEvent = { event = it }, onLocation = {}) }

        compose.onNodeWithText(context.getString(R.string.meeting_event)).performTextInput("Targi w Łodzi")
        compose.onNodeWithText(context.getString(R.string.meeting_location_purpose)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.meeting_add_location)).assertIsDisplayed()
        assertEquals("Targi w Łodzi", event)
    }

    @Test
    fun placeCanBeRemoved() {
        var location by mutableStateOf("52.40640, 16.92520")
        compose.setContent { MeetingSection("", location, enabled = true, onEvent = {}, onLocation = { location = it }) }

        compose.onNodeWithText("52.40640, 16.92520").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.meeting_remove_location)).performClick()
        assertEquals("", location)
    }
}
