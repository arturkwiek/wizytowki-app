package com.wizytowki.ui.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.ContactSummary
import com.wizytowki.data.db.entities.CompanyEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Company card: data and linked people (REQUIREMENTS 8a, screen 8). */
@RunWith(AndroidJUnit4::class)
class CompanyCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsCompanyAndOpensPerson() {
        var opened = 0L
        val details = CompanyDetails(
            CompanyEntity(id = 3, name = "Żabka Polska S.A.", nip = "5260250995"),
            listOf(ContactSummary(11, "Łucja", "Wiśniewska", "Żabka Polska S.A.", "Kierownik")),
        )
        compose.setContent { Column { CompanyCard(details) { opened = it } } }

        compose.onNodeWithText("Żabka Polska S.A.").assertIsDisplayed()
        compose.onNodeWithText("5260250995").assertIsDisplayed()
        compose.onNodeWithText("Łucja Wiśniewska").performClick()
        assertEquals(11L, opened)
    }
}
