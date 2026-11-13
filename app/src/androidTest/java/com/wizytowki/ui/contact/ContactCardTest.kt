package com.wizytowki.ui.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.R
import com.wizytowki.data.ContactDetails
import com.wizytowki.domain.ContactDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Person card content and quick actions (REQUIREMENTS 8a, screen 7). */
@RunWith(AndroidJUnit4::class)
class ContactCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsDataAndOpensCompany() {
        var opened = 0L
        val details = ContactDetails(
            1, 42,
            ContactDraft(firstName = "Łukasz", lastName = "Żółkiewski", emails = listOf("l.z@acme.pl"), company = "ACME S.A.", positionPl = "Prezes"),
            emptyList(),
        )
        compose.setContent { Column { ContactCard(details) { opened = it } } }

        compose.onNodeWithText("Łukasz Żółkiewski").assertIsDisplayed()
        compose.onNodeWithText("Prezes").assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.contact_call)) and hasClickAction()).assertIsNotEnabled()
        compose.onAllNodes(hasText(context.getString(R.string.contact_email)) and hasClickAction()).onFirst().assertIsEnabled()
        compose.onNodeWithText("ACME S.A.").performClick()
        assertEquals(42L, opened)
    }
}
