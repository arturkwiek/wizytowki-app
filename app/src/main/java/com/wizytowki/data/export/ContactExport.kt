package com.wizytowki.data.export

import com.wizytowki.data.ContactDetails
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** File formats of the contact export (#122). */
enum class ExportFormat(val extension: String, val mimeType: String) {
    /** vCard 3.0 – phone contacts, Google Contacts, Outlook. */
    VCARD("vcf", "text/x-vcard"),

    /** One row per person – Excel and spreadsheets. */
    CSV("csv", "text/csv"),

    /** Everything, including job history and scanned cards – for programs. */
    JSON("json", "application/json"),
}

/** Writes saved contacts in an [ExportFormat] (#122). Pure Kotlin; the file is written by the caller. */
object ContactExport {

    fun write(format: ExportFormat, contacts: List<ContactDetails>, exportedAt: LocalDateTime = LocalDateTime.now()): String =
        when (format) {
            ExportFormat.VCARD -> contacts.joinToString("") { vCard(it) }
            ExportFormat.CSV -> csv(contacts)
            ExportFormat.JSON -> json(contacts, exportedAt)
        }

    /** Suggested file name, e.g. "wizytowki-2026-11-14.vcf". */
    fun fileName(format: ExportFormat, date: LocalDateTime = LocalDateTime.now()) =
        "wizytowki-${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}.${format.extension}"

    // --- vCard 3.0 (RFC 2426) --------------------------------------------------------------

    fun vCard(details: ContactDetails): String {
        val c = details.contact
        val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
        lines += "N:${esc(c.lastName)};${esc(c.firstName)};;;"
        lines += "FN:${esc(fullName(details).ifBlank { c.company })}"
        if (c.company.isNotBlank() || department(details).isNotBlank()) {
            lines += "ORG:${esc(c.company)}" + department(details).takeIf { it.isNotBlank() }?.let { ";${esc(it)}" }.orEmpty()
        }
        position(details).takeIf { it.isNotBlank() }?.let { lines += "TITLE:${esc(it)}" }
        c.phones.forEach { lines += "TEL;TYPE=VOICE:${esc(it)}" }
        c.emails.forEach { lines += "EMAIL;TYPE=INTERNET:${esc(it)}" }
        (c.websites + c.social).forEach { lines += "URL:${esc(it)}" }
        listOf(c.addressPl, c.addressEn).filter { it.isNotBlank() }.distinct().forEach {
            // The address is stored as one line, so it goes into the street part.
            lines += "ADR;TYPE=WORK:;;${esc(it)};;;;"
        }
        note(details).takeIf { it.isNotBlank() }?.let { lines += "NOTE:${esc(it)}" }
        lines += "END:VCARD"
        return lines.joinToString("") { fold(it) + CRLF }
    }

    /** Text value escaping of RFC 2426: backslash, comma, semicolon and new lines. */
    private fun esc(value: String) = value
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    /** Lines longer than 75 octets are folded (CRLF + space), never inside a UTF-8 character. */
    private fun fold(line: String): String {
        val out = StringBuilder()
        var octets = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (octets + size > MAX_LINE_OCTETS) {
                out.append(CRLF).append(' ')
                octets = 1
            }
            out.appendCodePoint(cp)
            octets += size
            i += chars
        }
        return out.toString()
    }

    // --- CSV (RFC 4180) --------------------------------------------------------------------

    private val CSV_COLUMNS = listOf(
        "first_name", "last_name", "company", "nip", "position_pl", "position_en", "department_pl", "department_en",
        "phones", "emails", "websites", "social", "address_pl", "address_en", "comment",
        "cards", "last_scan", "last_event", "last_location",
    )

    /** UTF-8 with BOM, so Excel shows Polish letters; several values in one cell are separated by " | ". */
    fun csv(contacts: List<ContactDetails>): String {
        val rows = contacts.map { d ->
            val c = d.contact
            val last = d.cards.maxByOrNull { it.scannedAt }
            listOf(
                c.firstName, c.lastName, c.company, c.nip, c.positionPl, c.positionEn, c.departmentPl, c.departmentEn,
                c.phones.joinToString(MULTI), c.emails.joinToString(MULTI), c.websites.joinToString(MULTI), c.social.joinToString(MULTI),
                c.addressPl, c.addressEn, c.comment,
                d.cards.size.toString(), last?.scannedAt?.format(DATE_TIME).orEmpty(), last?.event.orEmpty(), last?.location.orEmpty(),
            )
        }
        return BOM + (listOf(CSV_COLUMNS) + rows).joinToString("") { row -> row.joinToString(",") { csvCell(it) } + CRLF }
    }

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    // --- JSON ------------------------------------------------------------------------------

    fun json(contacts: List<ContactDetails>, exportedAt: LocalDateTime): String {
        val j = JsonWriter()
        j.obj {
            field("format", "wizytowki-export")
            field("version", 1)
            field("exported_at", exportedAt.format(DATE_TIME))
            array("contacts", contacts) { d -> contactJson(this, d) }
        }
        return j.toString()
    }

    private fun contactJson(j: JsonWriter, d: ContactDetails) = with(j) {
        val c = d.contact
        obj {
            field("first_name", c.firstName)
            field("last_name", c.lastName)
            strings("phones", c.phones)
            strings("emails", c.emails)
            strings("websites", c.websites)
            strings("social", c.social)
            objField("company") {
                field("name", c.company)
                field("nip", c.nip)
                field("address_pl", c.addressPl)
                field("address_en", c.addressEn)
            }
            objField("position") {
                field("pl", c.positionPl)
                field("en", c.positionEn)
            }
            objField("department") {
                field("pl", c.departmentPl)
                field("en", c.departmentEn)
            }
            field("comment", c.comment)
            array("jobs", d.jobs) { job ->
                obj {
                    field("company", job.company)
                    field("position", job.position)
                    field("department", job.department)
                    field("from", job.dateFrom?.format(DATE_TIME))
                    field("to", job.dateTo?.format(DATE_TIME))
                    field("current", job.current)
                }
            }
            array("cards", d.cards) { card ->
                obj {
                    field("scanned_at", card.scannedAt.format(DATE_TIME))
                    field("event", card.event)
                    field("location", card.location)
                    field("ocr_text", card.rawOcrText)
                }
            }
        }
    }

    // --- shared ----------------------------------------------------------------------------

    private fun fullName(d: ContactDetails) = listOf(d.contact.firstName, d.contact.lastName).filter { it.isNotBlank() }.joinToString(" ")

    private fun position(d: ContactDetails) = d.contact.positionPl.ifBlank { d.contact.positionEn }

    private fun department(d: ContactDetails) = d.contact.departmentPl.ifBlank { d.contact.departmentEn }

    /** Comment plus where the contact was met, for formats without separate fields. */
    private fun note(d: ContactDetails): String {
        val met = d.cards.filter { it.event.isNotBlank() }.map { it.event }.distinct()
        return listOfNotNull(d.contact.comment.takeIf { it.isNotBlank() }, met.takeIf { it.isNotEmpty() }?.joinToString(", "))
            .joinToString("\n")
    }

    private const val CRLF = "\r\n"
    private const val MAX_LINE_OCTETS = 75
    private const val MULTI = " | "
    /** Byte order mark, so Excel reads the CSV as UTF-8. */
    private val BOM = Char(0xFEFF).toString()
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
}

/** Minimal JSON writer: objects, arrays, strings, numbers, booleans and null, with proper escaping. */
internal class JsonWriter {
    private val out = StringBuilder()

    /** For each open object or array: whether it already has an element (so the next needs a comma). */
    private val hasElements = ArrayDeque<Boolean>()

    /** A key was just written, so the next value belongs to it and needs no comma. */
    private var afterKey = false

    /** An object as an array element or the top-level value. */
    fun obj(body: JsonWriter.() -> Unit) = container('{', '}') { body() }

    fun objField(name: String, body: JsonWriter.() -> Unit) {
        key(name)
        container('{', '}') { body() }
    }

    fun <T> array(name: String, items: List<T>, item: JsonWriter.(T) -> Unit) {
        key(name)
        container('[', ']') { items.forEach { item(it) } }
    }

    fun strings(name: String, values: List<String>) = array(name, values) { element(quote(it)) }

    fun field(name: String, value: String?) {
        key(name)
        element(value?.let(::quote) ?: "null")
    }

    fun field(name: String, value: Int) {
        key(name)
        element(value.toString())
    }

    fun field(name: String, value: Boolean) {
        key(name)
        element(value.toString())
    }

    private fun key(name: String) {
        beginElement()
        out.append(quote(name)).append(':')
        afterKey = true
    }

    private fun element(text: String) {
        beginElement()
        out.append(text)
    }

    private fun container(open: Char, close: Char, body: () -> Unit) {
        beginElement()
        out.append(open)
        hasElements.addLast(false)
        body()
        hasElements.removeLast()
        out.append(close)
    }

    /** Writes the comma before a new element of the enclosing container, unless it is a key's value. */
    private fun beginElement() {
        if (afterKey) {
            afterKey = false
            return
        }
        if (hasElements.isNotEmpty()) {
            if (hasElements.last()) out.append(',')
            hasElements[hasElements.lastIndex] = true
        }
    }

    override fun toString() = out.toString()

    companion object {
        fun quote(text: String): String {
            val sb = StringBuilder("\"")
            for (ch in text) {
                when {
                    ch == '"' -> sb.append("\\\"")
                    ch == '\\' -> sb.append("\\\\")
                    ch == '\n' -> sb.append("\\n")
                    ch == '\r' -> sb.append("\\r")
                    ch == '\t' -> sb.append("\\t")
                    ch < ' ' -> sb.append(String.format("\\u%04x", ch.code))
                    else -> sb.append(ch)
                }
            }
            return sb.append('"').toString()
        }
    }
}
