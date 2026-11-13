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
import java.time.LocalDateTime
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.data.JobHistoryItem
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
        compose.setContent { Column { ContactCard(details, onOpenCompany = { opened = it }) } }

        compose.onNodeWithText("Łukasz Żółkiewski").assertIsDisplayed()
        compose.onNodeWithText("Prezes").assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.contact_call)) and hasClickAction()).assertIsNotEnabled()
        compose.onAllNodes(hasText(context.getString(R.string.contact_email)) and hasClickAction()).onFirst().assertIsEnabled()
        compose.onNodeWithText("ACME S.A.").performClick()
        assertEquals(42L, opened)
    }

    @Test
    fun showsJobHistoryAndCards() {
        val details = ContactDetails(
            1, 2, ContactDraft(firstName = "Anna", company = "Nowa Firma S.A.", positionPl = "Kierownik"),
            listOf(BusinessCardEntity(id = 1, personId = 1, frontPhotoPath = "/missing.jpg", event = "Targi 2026")),
            listOf(
                JobHistoryItem(2, "Nowa Firma S.A.", "Kierownik", "", LocalDateTime.of(2026, 3, 1, 0, 0), null, current = true),
                JobHistoryItem(1, "Stara Firma Sp. z o.o.", "Analityk", "", LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2026, 2, 28, 0, 0), current = false),
            ),
        )
        compose.setContent { Column { ContactCard(details, onOpenCompany = {}) } }

        compose.onNodeWithText(context.getString(R.string.contact_history)).assertIsDisplayed()
        compose.onNodeWithText("Analityk · Stara Firma Sp. z o.o.").assertIsDisplayed()
        compose.onNodeWithText("01.2024 – 02.2026").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.contact_cards, 1)).assertIsDisplayed()
        compose.onNodeWithText("Targi 2026").assertIsDisplayed()
    }

    @Test
    fun deletingACardIsConfirmedFirst() {
        var deleted: BusinessCardEntity? = null
        val card = BusinessCardEntity(id = 4, personId = 1, frontPhotoPath = "/missing.jpg", event = "Targi")
        val details = ContactDetails(1, null, ContactDraft(firstName = "Ola"), listOf(card))
        compose.setContent { Column { ContactCard(details, onOpenCompany = {}, onDeleteCard = { deleted = it }) } }

        compose.onNodeWithText(context.getString(R.string.delete_card)).performClick()
        assertEquals(null, deleted)
        compose.onNodeWithText(context.getString(R.string.delete_confirm)).performClick()
        assertEquals(4L, deleted?.id)
    }
}
