package com.wizytowki.domain

import com.wizytowki.extract.Entity
import com.wizytowki.extract.EntityType
import com.wizytowki.extract.Side
import com.wizytowki.ocr.Language
import java.text.Normalizer

/**
 * One field of the merged card record (WF-14).
 * - [language]: PL or EN for [EntityType.languageDependent] fields (WF-16), null otherwise or when unknown.
 * - [sides]: card sides the value was read from; both when it was repeated (WF-15).
 * - [alternatives]: other values read for the same field. When they come from the other side,
 *   [conflict] is true and the user has to choose (WF-15, Etap 3 UI).
 * - [confidence]: 0..1, see [FieldConfidence]; [lowConfidence] marks the field for checking (WF-22).
 */
data class MergedField(
    val type: EntityType,
    val value: String,
    val confidence: Float,
    val sides: Set<Side>,
    val language: Language? = null,
    val entity: Entity,
    val alternatives: List<Entity> = emptyList(),
    val conflict: Boolean = false,
) {
    val lowConfidence: Boolean get() = confidence < FieldConfidence.LOW
}

/** The card as one record built from both sides (WF-14). */
data class MergedCard(val fields: List<MergedField>) {
    val conflicts: List<MergedField> get() = fields.filter { it.conflict }

    fun field(type: EntityType, language: Language? = null): MergedField? =
        fields.firstOrNull { it.type == type && it.language == language }

    fun all(type: EntityType): List<MergedField> = fields.filter { it.type == type }
}

/**
 * Merges the entities of both card sides into one record (WF-14 – WF-17):
 * - the same value on both sides is stored once, with both sides and the higher confidence;
 *   values are compared without case, diacritics, spaces and punctuation, so "Lukasz" read
 *   without the Polish letter matches "Łukasz" (the version with diacritics is kept);
 * - phones, e-mails, websites, profiles and addresses may have several values – all are kept;
 * - name, company, NIP, and position/department per language have one value – when the sides
 *   disagree the field is marked as a [MergedField.conflict] with the other values as alternatives;
 * - position, department and address are kept separately per language, so a Polish front and
 *   an English back give both versions instead of a conflict (WF-16).
 */
object CardMerger {

    fun merge(entities: List<Entity>): MergedCard {
        val fields = entities
            .groupBy { it.type to slotLanguage(it) }
            .flatMap { (key, group) -> mergeSlot(key.first, key.second, group) }
            .sortedWith(compareBy({ it.type.ordinal }, { it.language?.ordinal ?: Int.MAX_VALUE }))
        return MergedCard(fields)
    }

    private fun slotLanguage(entity: Entity): Language? =
        if (entity.type.languageDependent) entity.language?.takeIf { it == Language.PL || it == Language.EN } else null

    private fun mergeSlot(type: EntityType, language: Language?, group: List<Entity>): List<MergedField> {
        val sameValues = group.groupBy { key(type, it.value) }.values.map { combine(type, language, it) }
            .sortedByDescending { it.confidence }
        if (type in MULTI_VALUED) return sameValues

        val best = sameValues.first()
        val others = sameValues.drop(1)
        if (others.isEmpty()) return listOf(best)
        // Two candidates read on the same side are an extraction ambiguity, not contradictory data.
        val conflict = others.any { other -> !best.sides.containsAll(other.sides) }
        val confidence = if (conflict) FieldConfidence.conflicted(best.confidence) else best.confidence
        return listOf(best.copy(alternatives = others.map { it.entity }, conflict = conflict, confidence = confidence))
    }

    /** One value read once or several times (e.g. on both sides). */
    private fun combine(type: EntityType, language: Language?, same: List<Entity>): MergedField {
        val chosen = same.maxWith(compareBy<Entity>({ diacritics(it.value) }, { it.confidence }))
        return MergedField(
            type = type,
            value = chosen.value,
            confidence = FieldConfidence.repeated(same.groupBy { it.side }.values.map { side -> side.maxOf { it.confidence } }),
            sides = same.map { it.side }.toSet(),
            language = language,
            entity = chosen,
        )
    }

    /** Comparison key: phones and NIP are already normalized; text loses case, diacritics and punctuation. */
    internal fun key(type: EntityType, value: String): String = when (type) {
        EntityType.PHONE, EntityType.NIP -> value
        EntityType.WEBSITE, EntityType.SOCIAL ->
            fold(value).removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
        EntityType.EMAIL -> value.lowercase()
        else -> fold(value).filter { it.isLetterOrDigit() }
    }

    private fun fold(text: String) = Normalizer.normalize(text.lowercase().replace('ł', 'l'), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private fun diacritics(text: String) = text.count { it.code > 127 }

    private val MULTI_VALUED = setOf(EntityType.PHONE, EntityType.EMAIL, EntityType.WEBSITE, EntityType.SOCIAL, EntityType.ADDRESS)
}
