package com.wizytowki.ui.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wizytowki.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Card photo on the full screen from a thumbnail (#118). */
@RunWith(AndroidJUnit4::class)
class PhotoViewerTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun photo(): String {
        val bitmap = Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val file = File(context.cacheDir, "viewer_test.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file).toString()
    }

    @Test
    fun thumbnailOpensAndClosesFullScreenPhoto() {
        val uri = photo()
        compose.setContent { CardThumbnails(listOf(uri)) }

        compose.onNodeWithContentDescription(context.getString(R.string.preview_side_front)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(context.getString(R.string.photo_full_screen)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(context.getString(R.string.photo_full_screen)).assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.photo_close)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.photo_full_screen)).assertDoesNotExist()
    }
}
