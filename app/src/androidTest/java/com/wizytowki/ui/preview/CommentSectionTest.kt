package com.wizytowki.ui.preview

import android.speech.RecognizerIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Comment typed or dictated with one tap (WF-23, UI-08). */
@RunWith(AndroidJUnit4::class)
class CommentSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun typedCommentAndMicrophoneButton() {
        var comment by mutableStateOf("")
        compose.setContent { CommentSection(comment, enabled = true, onEdit = { comment = it }, onDictated = {}) }

        compose.onNodeWithText(context.getString(R.string.preview_add_comment)).performTextInput("Spotkanie w Łodzi")
        compose.onNodeWithContentDescription(context.getString(R.string.preview_dictate)).assertIsEnabled()
        assertEquals("Spotkanie w Łodzi", comment)
    }

    @Test
    fun microphoneDisabledAfterSaving() {
        compose.setContent { CommentSection("", enabled = false, onEdit = {}, onDictated = {}) }
        compose.onNodeWithContentDescription(context.getString(R.string.preview_dictate)).assertIsNotEnabled()
    }

    @Test
    fun dictationAsksForOfflinePolishOrEnglish() {
        val intent = dictationIntent(DictationLanguage.PL)
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("pl-PL", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("en-US", dictationIntent(DictationLanguage.EN).getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
    }
}
