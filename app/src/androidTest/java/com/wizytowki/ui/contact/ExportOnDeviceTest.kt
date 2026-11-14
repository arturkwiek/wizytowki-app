package com.wizytowki.ui.contact

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.domain.ContactDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Export pieces that need Android (#122): writing the file and the phone's new-contact form. */
@RunWith(AndroidJUnit4::class)
class ExportOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exportIsWrittenAsUtf8AndReplacesOldContent() = runBlocking {
        val file = File(context.cacheDir, "export_test.vcf").apply { writeText("stara, dłuższa zawartość pliku") }
        val target = ContentResolverExportTarget(context)

        target.write(Uri.fromFile(file).toString(), "Żółć")

        assertEquals("Żółć", file.readText(Charsets.UTF_8))
    }

    @Test
    fun phoneContactFormGetsAllData() {
        val intent = QuickActions.addToPhoneContacts(
            ContactDraft(
                firstName = "Łukasz",
                lastName = "Żółkiewski",
                phones = listOf("+48600100200", "+48221234567"),
                emails = listOf("l.z@acme.pl"),
                websites = listOf("www.acme.pl"),
                company = "ACME S.A.",
                positionPl = "Prezes",
                addressPl = "ul. Długa 5, 00-001 Warszawa",
                comment = "Targi",
            ),
        )

        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals("Łukasz Żółkiewski", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
        assertEquals("Targi", intent.getStringExtra(ContactsContract.Intents.Insert.NOTES))
        @Suppress("DEPRECATION")
        val rows = intent.getParcelableArrayListExtra<ContentValues>(ContactsContract.Intents.Insert.DATA)!!
        fun of(mime: String) = rows.filter { it.getAsString(ContactsContract.Data.MIMETYPE) == mime }
        assertEquals(listOf("+48600100200", "+48221234567"), of(CommonDataKinds.Phone.CONTENT_ITEM_TYPE).map { it.getAsString(CommonDataKinds.Phone.NUMBER) })
        assertEquals("l.z@acme.pl", of(CommonDataKinds.Email.CONTENT_ITEM_TYPE).single().getAsString(CommonDataKinds.Email.ADDRESS))
        assertEquals("www.acme.pl", of(CommonDataKinds.Website.CONTENT_ITEM_TYPE).single().getAsString(CommonDataKinds.Website.URL))
        val org = of(CommonDataKinds.Organization.CONTENT_ITEM_TYPE).single()
        assertEquals("ACME S.A.", org.getAsString(CommonDataKinds.Organization.COMPANY))
        assertEquals("Prezes", org.getAsString(CommonDataKinds.Organization.TITLE))
        assertEquals(
            "ul. Długa 5, 00-001 Warszawa",
            of(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE).single().getAsString(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
        )
    }
}
