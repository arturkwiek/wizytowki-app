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
import com.wizytowki.domain.CompanyMatcher
import com.wizytowki.domain.SavedCompany
import com.wizytowki.domain.SavedContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One scan to keep with the contact (WF-24, WF-25, WF-34): photo URIs from the draft, the raw OCR
 * text and, optionally, where ([location], "lat, lon") and at which [event] the contact was met.
 */
data class ScanRecord(
    val frontUri: String,
    val backUri: String?,
    val rawText: String,
    val event: String = "",
    val location: String = "",
)

/**
 * What one save changed, so it can be undone (#25). [cardPhotos] are the kept photos of the
 * scan (front, then back if any) – the scan can be restored from them.
 */
sealed interface SavedChange {
    val personId: Long
    val cardId: Long
    val cardPhotos: List<String>

    /**
     * A new person, with the company created for them ([companyId]) or the existing company they
     * were linked to, as it was before its empty fields were filled in ([linkedCompanyBefore]).
     */
    data class Created(
        override val personId: Long,
        override val cardId: Long,
        override val cardPhotos: List<String>,
        val companyId: Long?,
        val linkedCompanyBefore: CompanyEntity? = null,
    ) : SavedChange

    /** An existing person updated: their records as they were before. */
    data class Updated(
        override val personId: Long,
        override val cardId: Long,
        override val cardPhotos: List<String>,
        val personBefore: PersonEntity,
        val companyBefore: CompanyEntity?,
        val jobsBefore: List<EmploymentHistoryEntity>,
        /** Company created by this update (new employer), deleted on undo. */
        val createdCompanyId: Long?,
        /** Existing company the person moved to, as it was before its empty fields were filled in. */
        val linkedCompanyBefore: CompanyEntity? = null,
    ) : SavedChange
}

/** Saved contacts (#98). */
interface ContactRepository {
    /** Saves a new person (with company, position and the scanned card). */
    suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange

    /** All saved people with their current company and position, for finding duplicates (WF-32). */
    suspend fun savedContacts(): List<SavedContact>

    /**
     * Updates a saved person with the merged [contact] and adds the scan to their cards (WF-32, WF-34).
     * A different company ends the current job and starts a new one, keeping the history.
     */
    suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange

    /** Reverts [change] completely: database records and the kept photos of that scan (#25). */
    suspend fun undo(change: SavedChange)

    /** Merges two saved people into [keep] with the user's chosen data (WF-35). */
    suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft)
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

    override suspend fun saveNew(contact: ContactDraft, scan: ScanRecord): SavedChange {
        val card = keepPhotos(scan)
        val now = LocalDateTime.now()
        return db.withTransaction {
            val company = if (contact.hasCompanyData) linkCompany(contact, now) else null
            val companyId = company?.id
            val personId = db.personDao().insert(contact.toPerson(companyId, now))
            if (companyId != null) {
                db.employmentHistoryDao().insert(contact.toEmployment(personId, companyId, now))
            }
            val cardId = db.businessCardDao().insert(card.copy(personId = personId, scannedAt = now))
            SavedChange.Created(personId, cardId, card.photos(), companyId.takeIf { company?.created == true }, company?.before)
        }
    }

    override suspend fun update(saved: SavedContact, contact: ContactDraft, scan: ScanRecord): SavedChange {
        val card = keepPhotos(scan)
        val now = LocalDateTime.now()
        return db.withTransaction {
            val applied = applyContact(saved, contact, now)
            val cardId = db.businessCardDao().insert(card.copy(personId = saved.personId, scannedAt = now))
            SavedChange.Updated(
                saved.personId, cardId, card.photos(), applied.person, applied.company, applied.jobs,
                applied.createdCompanyId, applied.linkedCompanyBefore,
            )
        }
    }

    /**
     * Manual merge (WF-35, #32): [removeId] becomes part of [keep] – their cards and job history move
     * over (former jobs stay in the history as ended), [result] is the merged data chosen by the
     * user, comments of both are kept, and the second person record is deleted.
     */
    override suspend fun mergePeople(keep: SavedContact, removeId: Long, result: ContactDraft) {
        val now = LocalDateTime.now()
        db.withTransaction {
            val removed = db.personDao().getPersonById(removeId).first() ?: error("Person $removeId was deleted")
            db.businessCardDao().moveCards(removeId, keep.personId)
            db.employmentHistoryDao().moveJobs(removeId, keep.personId, now)
            db.personDao().delete(removeId)
            applyContact(keep, result.copy(comment = removed.comment), now)
        }
    }

    private class Applied(
        val person: PersonEntity,
        val company: CompanyEntity?,
        val jobs: List<EmploymentHistoryEntity>,
        val createdCompanyId: Long?,
        val linkedCompanyBefore: CompanyEntity?,
    )

    /** Writes [contact] over a saved person, their company and current job; call inside a transaction. */
    private suspend fun applyContact(saved: SavedContact, contact: ContactDraft, now: LocalDateTime): Applied {
        val personId = saved.personId
        val person = db.personDao().getPersonById(personId).first() ?: error("Person $personId was deleted")
        val company = saved.companyId?.let { db.companyDao().getCompanyById(it).first() }
        val jobs = db.employmentHistoryDao().getHistoryByPerson(personId).first()
        // A company record without a name (only position on the card) stays the same company.
        val sameCompany = company != null &&
            (CompanyMatcher.matches(contact, company.toSaved()) || company.name.isBlank() && contact.company.isBlank())
        var createdCompanyId: Long? = null
        var linkedCompanyBefore: CompanyEntity? = null

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
                val link = linkCompany(contact, now)
                if (link.created) createdCompanyId = link.id else linkedCompanyBefore = link.before
                db.employmentHistoryDao().insert(contact.toEmployment(personId, link.id, now))
                link.id
            }
            else -> person.currentCompanyId
        }
        // Notes from earlier scans stay; a new note is added below them (WF-23).
        val comment = listOf(person.comment, contact.comment).filter { it.isNotBlank() }.distinct().joinToString("\n\n")
        db.personDao().update(contact.toPerson(companyId, now).copy(id = personId, comment = comment, createdAt = person.createdAt))
        return Applied(person, company, jobs, createdCompanyId, linkedCompanyBefore)
    }

    override suspend fun undo(change: SavedChange) {
        db.withTransaction {
            val jobs = db.employmentHistoryDao()
            when (change) {
                is SavedChange.Created -> {
                    db.businessCardDao().getCardsByPerson(change.personId).first().forEach { db.businessCardDao().delete(it.id) }
                    jobs.getHistoryByPerson(change.personId).first().forEach { jobs.delete(it.id) }
                    db.personDao().delete(change.personId)
                    change.companyId?.let { db.companyDao().delete(it) }
                    change.linkedCompanyBefore?.let { db.companyDao().update(it) }
                }
                is SavedChange.Updated -> {
                    db.businessCardDao().delete(change.cardId)
                    jobs.getHistoryByPerson(change.personId).first().forEach { jobs.delete(it.id) }
                    change.jobsBefore.forEach { jobs.insert(it) }
                    change.companyBefore?.let { db.companyDao().update(it) }
                    change.createdCompanyId?.let { db.companyDao().delete(it) }
                    change.linkedCompanyBefore?.let { db.companyDao().update(it) }
                    db.personDao().update(change.personBefore)
                }
            }
        }
        withContext(Dispatchers.IO) { change.cardPhotos.forEach { File(it).delete() } }
    }

    private class CompanyLink(val id: Long, val created: Boolean, val before: CompanyEntity?)

    /**
     * The company of a saved contact (WF-31, WF-33): an existing one when [CompanyMatcher] finds it –
     * only its empty fields are filled in, as other people share it – otherwise a new one.
     */
    private suspend fun linkCompany(contact: ContactDraft, now: LocalDateTime): CompanyLink {
        val all = db.companyDao().getAllCompanies().first()
        val match = CompanyMatcher.find(contact, all.map { it.toSaved() })?.let { m -> all.first { it.id == m.id } }
            ?: return CompanyLink(db.companyDao().insert(contact.toCompany(now)), created = true, before = null)
        val filled = match.fillBlanks(contact.toCompany(now))
        if (filled != match) db.companyDao().update(filled.copy(updatedAt = now))
        return CompanyLink(match.id, created = false, before = match)
    }

    private fun BusinessCardEntity.photos() = listOf(frontPhotoPath, backPhotoPath).filter { it.isNotEmpty() }

    private suspend fun keepPhotos(scan: ScanRecord): BusinessCardEntity {
        val stamp = System.currentTimeMillis()
        val front = photos.keep(scan.frontUri, "card_${stamp}_front")
        val back = scan.backUri?.let { photos.keep(it, "card_${stamp}_back") }.orEmpty()
        return BusinessCardEntity(
            personId = 0, frontPhotoPath = front, backPhotoPath = back, rawOcrText = scan.rawText,
            event = scan.event.trim(), location = scan.location,
        )
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
    comment = comment,
    currentCompanyId = companyId,
    createdAt = now,
    updatedAt = now,
)

internal fun CompanyEntity.toSaved() = SavedCompany(id, name, nip, website, emailDomain)

/** Fills only the empty fields of a shared company record with new data. */
internal fun CompanyEntity.fillBlanks(new: CompanyEntity) = copy(
    name = name.ifBlank { new.name },
    nip = nip.ifBlank { new.nip },
    website = website.ifBlank { new.website },
    emailDomain = emailDomain.ifBlank { new.emailDomain },
    addressPl = addressPl.ifBlank { new.addressPl },
    addressEn = addressEn.ifBlank { new.addressEn },
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
