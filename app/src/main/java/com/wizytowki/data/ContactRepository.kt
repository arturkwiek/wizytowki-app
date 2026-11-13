package com.wizytowki.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.wizytowki.data.db.WizytowkiDatabase
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.data.db.entities.CompanyEntity
import com.wizytowki.data.db.entities.EmploymentHistoryEntity
import com.wizytowki.data.db.entities.PersonEntity
import com.wizytowki.domain.ContactDraft
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** One scan to keep with the contact (WF-25, WF-34): photo URIs from the draft and the raw OCR text. */
data class ScanRecord(val frontUri: String, val backUri: String?, val rawText: String)

/** Saved contacts (#98). */
interface ContactRepository {
    /** Saves a new person (with company, position and the scanned card); returns the person id. */
    suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): Long
}

/** Card photos kept for good: moved out of the scan draft, which is deleted after saving. */
@Singleton
class CardPhotoStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "cards")

    /** Copies the photo to the permanent folder and returns its path. */
    suspend fun keep(uri: String, name: String): String = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = File(dir, "$name.jpg")
        val source = Uri.parse(uri).path?.let(::File)
        if (source != null && source.exists()) {
            source.copyTo(target, overwrite = true)
        } else {
            context.contentResolver.openInputStream(Uri.parse(uri))!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        target.path
    }
}

class RoomContactRepository @Inject constructor(
    private val db: WizytowkiDatabase,
    private val photos: CardPhotoStore,
) : ContactRepository {

    override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): Long {
        val stamp = System.currentTimeMillis()
        val front = photos.keep(scan.frontUri, "card_${stamp}_front")
        val back = scan.backUri?.let { photos.keep(it, "card_${stamp}_back") }.orEmpty()
        val now = LocalDateTime.now()
        return db.withTransaction {
            val companyId = if (contact.hasCompanyData) db.companyDao().insert(contact.toCompany(now)) else null
            val personId = db.personDao().insert(contact.toPerson(companyId, now))
            if (companyId != null) {
                db.employmentHistoryDao().insert(contact.toEmployment(personId, companyId, now))
            }
            db.businessCardDao().insert(
                BusinessCardEntity(personId = personId, frontPhotoPath = front, backPhotoPath = back, rawOcrText = scan.rawText, scannedAt = now),
            )
            personId
        }
    }
}

/** Several values in one column, one per line (the schema of Etap 0). */
internal fun List<String>.joinLines() = filter { it.isNotBlank() }.distinct().joinToString("\n")

internal fun String.splitLines() = split('\n').map { it.trim() }.filter { it.isNotEmpty() }

internal fun ContactDraft.toPerson(companyId: Long?, now: LocalDateTime) = PersonEntity(
    firstName = firstName,
    lastName = lastName,
    phones = phones.joinLines(),
    emails = emails.joinLines(),
    websites = websites.joinLines(),
    socialProfiles = social.joinLines(),
    currentCompanyId = companyId,
    createdAt = now,
    updatedAt = now,
)

internal fun ContactDraft.toCompany(now: LocalDateTime) = CompanyEntity(
    name = company,
    nip = nip,
    website = websites.firstOrNull().orEmpty(),
    emailDomain = emails.firstOrNull()?.substringAfter('@', "").orEmpty(),
    addressPl = addressPl,
    addressEn = addressEn,
    createdAt = now,
    updatedAt = now,
)

internal fun ContactDraft.toEmployment(personId: Long, companyId: Long, now: LocalDateTime) = EmploymentHistoryEntity(
    personId = personId,
    companyId = companyId,
    positionPl = positionPl,
    positionEn = positionEn,
    departmentPl = departmentPl,
    departmentEn = departmentEn,
    dateFrom = now,
    isCurrent = true,
)
