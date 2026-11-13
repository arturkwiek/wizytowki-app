package com.wizytowki.data

import com.wizytowki.data.db.WizytowkiDatabase
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.data.db.entities.CompanyEntity
import com.wizytowki.data.db.entities.EmploymentHistoryEntity
import com.wizytowki.domain.ContactDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDateTime
import javax.inject.Inject

/** One row of a contact list: who, where and as what. */
data class ContactSummary(
    val personId: Long,
    val firstName: String,
    val lastName: String,
    val company: String,
    val position: String,
) {
    val name: String get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
}

/** One job in the person's history (WF-34): company, position and dates; [dateTo] null while current. */
data class JobHistoryItem(
    val companyId: Long,
    val company: String,
    val position: String,
    val department: String,
    val dateFrom: LocalDateTime?,
    val dateTo: LocalDateTime?,
    val current: Boolean,
)

/**
 * Everything the person card shows (REQUIREMENTS 8a, screen 7; WF-34): current data, the job
 * history (newest first) and all scanned cards (newest first).
 */
data class ContactDetails(
    val personId: Long,
    val companyId: Long?,
    val contact: ContactDraft,
    val cards: List<BusinessCardEntity>,
    val jobs: List<JobHistoryItem> = emptyList(),
)

/** Everything the company card shows (REQUIREMENTS 8a, screen 8): the company and its people. */
data class CompanyDetails(val company: CompanyEntity, val people: List<ContactSummary>)

/** Reading saved contacts for the screens; saving goes through [ContactRepository]. */
interface ContactQueries {
    fun recentContacts(limit: Int = 20): Flow<List<ContactSummary>>

    fun contact(personId: Long): Flow<ContactDetails?>

    fun company(companyId: Long): Flow<CompanyDetails?>
}

class RoomContactQueries @Inject constructor(private val db: WizytowkiDatabase) : ContactQueries {

    override fun recentContacts(limit: Int): Flow<List<ContactSummary>> = combine(
        db.personDao().recentPersons(limit),
        db.companyDao().getAllCompanies(),
        db.employmentHistoryDao().currentJobs(),
    ) { persons, companies, jobs ->
        val companyNames = companies.associate { it.id to it.name }
        val jobByPerson = jobs.associateBy { it.personId }
        persons.map { person ->
            val job = jobByPerson[person.id]
            ContactSummary(
                personId = person.id,
                firstName = person.firstName,
                lastName = person.lastName,
                company = person.currentCompanyId?.let { companyNames[it] }.orEmpty(),
                position = job?.positionPl?.ifBlank { job.positionEn }.orEmpty(),
            )
        }
    }

    override fun contact(personId: Long): Flow<ContactDetails?> = combine(
        db.personDao().getPersonById(personId),
        db.companyDao().getAllCompanies(),
        db.employmentHistoryDao().getHistoryByPerson(personId),
        db.businessCardDao().getCardsByPerson(personId),
    ) { person, companies, jobs, cards ->
        person ?: return@combine null
        val company = person.currentCompanyId?.let { id -> companies.firstOrNull { it.id == id } }
        val job = jobs.firstOrNull { it.isCurrent }
        val names = companies.associate { it.id to it.name }
        val history = jobs
            .sortedWith(compareByDescending<EmploymentHistoryEntity> { it.isCurrent }.thenByDescending { it.dateFrom })
            .map {
                JobHistoryItem(
                    companyId = it.companyId,
                    company = names[it.companyId].orEmpty(),
                    position = it.positionPl.ifBlank { it.positionEn },
                    department = it.departmentPl.ifBlank { it.departmentEn },
                    dateFrom = it.dateFrom,
                    dateTo = it.dateTo,
                    current = it.isCurrent,
                )
            }
        ContactDetails(person.id, company?.id, contactOf(person, company, job).copy(comment = person.comment), cards, history)
    }

    override fun company(companyId: Long): Flow<CompanyDetails?> = combine(
        db.companyDao().getCompanyById(companyId),
        db.personDao().personsOfCompany(companyId),
        db.employmentHistoryDao().currentJobs(),
    ) { company, persons, jobs ->
        company ?: return@combine null
        val jobByPerson = jobs.associateBy { it.personId }
        CompanyDetails(
            company,
            persons.map { person ->
                val job = jobByPerson[person.id]
                ContactSummary(person.id, person.firstName, person.lastName, company.name, job?.positionPl?.ifBlank { job.positionEn }.orEmpty())
            },
        )
    }
}
