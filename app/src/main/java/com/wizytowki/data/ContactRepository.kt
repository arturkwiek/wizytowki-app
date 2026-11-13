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
import com.wizytowki.domain.SavedContact
import com.wizytowki.domain.fold
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    /** All saved people with their current company and position, for finding duplicates (WF-32). */
    suspend fun savedContacts(): List<SavedContact>

    /**
     * Updates a saved person with the merged [contact] and adds the scan to their cards (WF-32, WF-34).
     * A different company ends the current job and starts a new one, keeping the history.
     */
    suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord)
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
        val card = keepPhotos(scan)
        val now = LocalDateTime.now()
        return db.withTransaction {
            val companyId = if (contact.hasCompanyData) db.companyDao().insert(contact.toCompany(now)) else null
            val personId = db.personDao().insert(contact.toPerson(companyId, now))
            if (companyId != null) {
                db.employmentHistoryDao().insert(contact.toEmployment(personId, companyId, now))
            }
            db.businessCardDao().insert(card.copy(personId = personId, scannedAt = now))
            personId
        }
    }

    override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord) {
        val card = keepPhotos(scan)
        val now = LocalDateTime.now()
        db.withTransaction {
            val personId = saved.personId
            val person = db.personDao().getPersonById(personId).first() ?: error("Person $personId was deleted")
            val company = saved.companyId?.let { db.companyDao().getCompanyById(it).first() }
            val jobs = db.employmentHistoryDao().getHistoryByPerson(personId).first()
            val sameCompany = company != null && fold(company.name) == fold(contact.company)

            val companyId = when {
                sameCompany -> {
                    val updated = contact.toCompany(now)
                    db.companyDao().update(updated.copy(id = company!!.id, createdAt = company.createdAt))
                    jobs.firstOrNull { it.isCurrent && it.companyId == company.id }?.let { job ->
                        db.employmentHistoryDao().update(contact.toEmployment(personId, company.id, now).copy(id = job.id, dateFrom = job.dateFrom))
                    } ?: db.employmentHistoryDao().insert(contact.toEmployment(personId, company.id, now))
                    company.id
                }
                contact.hasCompanyData -> {
                    // New employer: the previous job stays in the history (WF-34).
                    jobs.filter { it.isCurrent }.forEach { db.employmentHistoryDao().update(it.copy(isCurrent = false, dateTo = now)) }
                    val id = db.companyDao().insert(contact.toCompany(now))
                    db.employmentHistoryDao().insert(contact.toEmployment(personId, id, now))
                    id
                }
                else -> person.currentCompanyId
            }
            db.personDao().update(contact.toPerson(companyId, now).copy(id = personId, comment = person.comment, createdAt = person.createdAt))
            db.businessCardDao().insert(card.copy(personId = personId, scannedAt = now))
        }
    }

    private suspend fun keepPhotos(scan: ScanRecord): BusinessCardEntity {
        val stamp = System.currentTimeMillis()
        val front = photos.keep(scan.frontUri, "card_${stamp}_front")
        val back = scan.backUri?.let { photos.keep(it, "card_${stamp}_back") }.orEmpty()
        return BusinessCardEntity(personId = 0, frontPhotoPath = front, backPhotoPath = back, rawOcrText = scan.rawText)
    }

    override suspend fun savedContacts(): List<SavedContact> = db.withTransaction {
        db.personDao().getAllPersons().first().map { person ->
            val company = person.currentCompanyId?.let { db.companyDao().getCompanyById(it).first() }
            val job = db.employmentHistoryDao().getHistoryByPerson(person.id).first().firstOrNull { it.isCurrent }
            SavedContact(person.id, company?.id, contactOf(person, company, job))
        }
    }
}

internal fun contactOf(person: PersonEntity, company: CompanyEntity?, job: EmploymentHistoryEntity?) = ContactDraft(
    firstName = person.firstName,
    lastName = person.lastName,
    phones = person.phones.splitLines(),
    emails = person.emails.splitLines(),
    websites = person.websites.splitLines(),
    social = person.socialProfiles.splitLines(),
    company = company?.name.orEmpty(),
    nip = company?.nip.orEmpty(),
    positionPl = job?.positionPl.orEmpty(),
    positionEn = job?.positionEn.orEmpty(),
    departmentPl = job?.departmentPl.orEmpty(),
    departmentEn = job?.departmentEn.orEmpty(),
    addressPl = company?.addressPl.orEmpty(),
    addressEn = company?.addressEn.orEmpty(),
)

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
