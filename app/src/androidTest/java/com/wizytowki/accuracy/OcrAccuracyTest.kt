package com.wizytowki.accuracy

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.domain.CardMerger
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.ocr.CardRecognizer
import com.wizytowki.ocr.FragmentLanguageDetector
import com.wizytowki.ocr.Language
import com.wizytowki.ocr.MlKitLanguageIdentifier
import com.wizytowki.ocr.MlKitTextRecognizer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Etap 2 acceptance test (#19): the real pipeline – ML Kit OCR, language detection, extraction,
 * merging – on the photos in `test-data/cards/` against `test-data/expected/`, plus the same layout
 * in the git-ignored `test-data/private/` for real cards (both bundled into the test APK only, by
 * Gradle). Criterion: ≥ 90 % of fields correct and analysis under 3 s per card (WNF-02).
 *
 * Run on a device or emulator: `./gradlew connectedDebugAndroidTest`; the report is in logcat
 * (tag OcrAccuracy) and in the assertion message. Skipped while the set has fewer than
 * [MIN_CARDS] cards, so the result is never computed on a sample too small to mean anything;
 * a trial run on fewer cards: `-Pandroid.testInstrumentationRunnerArguments.minCards=1`.
 */
@RunWith(AndroidJUnit4::class)
class OcrAccuracyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val assets = instrumentation.context.assets

    /** One test card: [dir] is the set ("test-data" or the local "test-data/private"), [id] e.g. "001". */
    private data class TestCard(val dir: String, val id: String) {
        val name: String get() = if (dir == ROOT) id else "${dir.removePrefix("$ROOT/")}/$id"
    }

    @Test
    fun fieldAccuracyAndTimeOnTestDataSet() = runBlocking {
        val cards = SETS.flatMap { dir ->
            val photos = assets.list("$dir/cards").orEmpty().toSet()
            assets.list("$dir/expected").orEmpty().filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }
                .filter { "${it}_front.jpg" in photos }
                .sorted()
                .map { TestCard(dir, it) }
        }
        val minCards = InstrumentationRegistry.getArguments().getString("minCards")?.toIntOrNull() ?: MIN_CARDS
        assumeTrue("test-data has ${cards.size} cards with photos, need at least $minCards", cards.size >= minCards)

        val recognizer = CardRecognizer(MlKitTextRecognizer(context), FragmentLanguageDetector(MlKitLanguageIdentifier()))
        val extractor = EntityExtractor()
        var report = AccuracyReport(emptyList())
        val times = mutableMapOf<String, Long>()
        for (card in cards) {
            val front = copy(card.dir, "cards/${card.id}_front.jpg")!!
            val back = copy(card.dir, "cards/${card.id}_back.jpg")
            val start = System.currentTimeMillis()
            val merged = CardMerger.merge(extractor.extract(recognizer.recognize(front, back)))
            times[card.name] = System.currentTimeMillis() - start
            val expected = assets.open("${card.dir}/expected/${card.id}.json").reader().use { parse(it.readText()) }
            report += AccuracyScorer.score(card.name, expected, merged)
        }

        val slow = times.filterValues { it >= MAX_MS }
        val summary = buildString {
            append(report.describe())
            // The first card also loads the ML Kit models, like the first scan after the app starts.
            appendLine(
                "Time per card: avg ${times.values.average().toLong()} ms, max ${times.values.max()} ms, " +
                    "first (cold start) ${times.values.first()} ms",
            )
            if (slow.isNotEmpty()) appendLine("Slower than $MAX_MS ms: " + slow.entries.joinToString { "${it.key} ${it.value} ms" })
        }
        summary.lines().forEach { Log.i(TAG, it) }
        assertTrue(summary, report.accuracy >= MIN_ACCURACY)
        assertTrue(summary, slow.isEmpty())
    }

    /** Copies an asset to a file the recognizer can open; null when the card has no such side. */
    private fun copy(dir: String, path: String): String? {
        val input = runCatching { assets.open("$dir/$path") }.getOrNull() ?: return null
        val file = File(context.cacheDir, "$dir/$path".replace('/', '_'))
        input.use { i -> file.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(file).toString()
    }

    private companion object {
        const val ROOT = "test-data"

        /** The committed set (fictional cards) and the local one with real cards, never committed. */
        val SETS = listOf(ROOT, "$ROOT/private")
        const val TAG = "OcrAccuracy"
        const val MIN_CARDS = 30
        const val MIN_ACCURACY = 0.9
        const val MAX_MS = 3000L
    }
}

/** Parses `test-data/expected/NNN.json` (see test-data/README.md). */
internal fun parse(json: String): ExpectedCard {
    val o = JSONObject(json)
    fun str(obj: JSONObject?, key: String) = obj?.takeIf { it.has(key) && !it.isNull(key) }?.getString(key)
    fun list(key: String): List<String> = when (val v = o.opt(key)) {
        is JSONArray -> (0 until v.length()).map { v.getString(it) }
        is String -> listOf(v)
        else -> emptyList()
    }
    fun versions(key: String): Map<Language, String> {
        val v = o.optJSONObject(key) ?: return emptyMap()
        return listOfNotNull(str(v, "pl")?.let { Language.PL to it }, str(v, "en")?.let { Language.EN to it }).toMap()
    }
    val company = o.optJSONObject("company")
    return ExpectedCard(
        firstName = str(o, "firstName"),
        lastName = str(o, "lastName"),
        phones = list("phones"),
        emails = list("emails"),
        websites = list("website") + list("websites"),
        social = list("social"),
        companyName = str(company, "name"),
        nip = str(company, "nip"),
        position = versions("position"),
        department = versions("department"),
        address = versions("address"),
    )
}
