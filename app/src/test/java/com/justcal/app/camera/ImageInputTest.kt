package com.justcal.app.camera

import com.justcal.app.ui.ImageAcquisition
import com.justcal.app.ui.PhotoReview
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ImageInputTest {
    @Test fun scanContextSurvivesNavigationSerializationAndReselection() {
        for (mode in ScanMode.entries) for (source in ImageSource.entries) {
            val request = ScanRequest(mode, LocalDate.of(2020, 2, 29).toEpochDay())
            val route = ImageAcquisition(request, source)
            val restored = Json.decodeFromString<ImageAcquisition>(Json.encodeToString(route))
            val image = PreparedImage("file:///private/test.image", restored.source, restored.request, 400, 300, "image/jpeg")
            val review = Json.decodeFromString<PhotoReview>(Json.encodeToString(PhotoReview(image)))
            assertEquals(route, ImageAcquisition(review.image.request, review.image.source))
            assertEquals(request, VerifiedImageInput(review.image).image.request)
        }
    }

    @Test fun smallImagesAreNeverUpscaledOrResized() {
        assertEquals(ImageSize(1024, 768), ImageDecodeBudget.fit(1024, 768))
        assertEquals(ImageSize(1, 1), ImageDecodeBudget.fit(1, 1))
    }

    @Test fun largeImagesAndPanoramasRespectBothBudgets() {
        for ((width, height) in listOf(8000 to 6000, 6000 to 8000, 200000 to 1, 1 to 200000,
            50000 to 50000, Int.MAX_VALUE to Int.MAX_VALUE)) {
            val size = ImageDecodeBudget.fit(width, height)
            assertTrue(size.width in 1..ImageDecodeBudget.MAX_EDGE)
            assertTrue(size.height in 1..ImageDecodeBudget.MAX_EDGE)
            assertTrue(size.width.toLong() * size.height <= ImageDecodeBudget.MAX_PIXELS)
            if (width == height) assertEquals(size.width, size.height)
        }
    }

    @Test fun portraitAndLandscapeHaveSymmetricBudgets() {
        val portrait = ImageDecodeBudget.fit(6000, 8000)
        val landscape = ImageDecodeBudget.fit(8000, 6000)
        assertEquals(portrait.width, landscape.height)
        assertEquals(portrait.height, landscape.width)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() { ImageDecodeBudget.fit(0, 100) }
}
