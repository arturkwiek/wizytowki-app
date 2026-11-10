package com.wizytowki.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real ML Kit on the device: a rendered card with Polish characters is read offline (WF-10, WNF-01). */
@RunWith(AndroidJUnit4::class)
class MlKitTextRecognizerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readsPolishCharactersOffline() = runBlocking {
        val lines = listOf("Łukasz Żółć", "Kierownik działu sprzedaży", "+48 600 123 456", "ul. Źródlana 5, 00-950 Wrocław")
        val uri = renderCard(lines)

        val page = MlKitTextRecognizer(context).recognize(uri.toString())

        val text = page.text
        lines.forEach { assertTrue("missing \"$it\" in:\n$text", text.contains(it)) }
        assertTrue(page.lines.all { it.confidence != null && it.box != null })
    }

    private fun renderCard(lines: List<String>): Uri {
        val bitmap = Bitmap.createBitmap(1700, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.SANS_SERIF
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 100f, 200f + i * 180f, paint) }
        val file = File(context.cacheDir, "ocr_test_card.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}
