package com.wizytowki.ui.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.R
import com.wizytowki.domain.CardMerger
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The editable contact form of the preview screen (WF-20, WF-22). */
@RunWith(AndroidJUnit4::class)
class ContactFormTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun editsFieldsAndAddsAPhone() {
        var draft by mutableStateOf(ContactDraft(firstName = "Lukasz", phones = listOf("+48600100200")))
        compose.setContent {
            ContactForm(draft, CardMerger.merge(emptyList()), setOf(ContactField.FIRST_NAME), enabled = true) { field, values ->
                draft = with(ContactComparison) { draft.with(field, values) }
            }
        }

        compose.onNodeWithText("Lukasz").performTextReplacement("Łukasz")
        compose.onAllNodesWithText(text(R.string.preview_low_confidence)).onFirst().assertExists()
        compose.onNodeWithText(context.getString(R.string.preview_add_value, text(R.string.field_phone))).performClick()

        assertEquals("Łukasz", draft.firstName)
        assertEquals(listOf("+48600100200", ""), draft.phones)
    }

    @Test
    fun savedContactCannotBeEdited() {
        compose.setContent {
            ContactForm(ContactDraft(firstName = "Anna"), CardMerger.merge(emptyList()), emptySet(), enabled = false) { _, _ -> }
        }
        compose.onNodeWithText("Anna").assertIsNotEnabled()
    }
}
