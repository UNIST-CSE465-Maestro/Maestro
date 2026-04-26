package com.maestro.app.ui.viewer

import android.content.Context
import android.net.Uri
import com.maestro.app.data.local.ExtractionProgressStore
import com.maestro.app.data.local.QuizResponseLocalDataSource
import com.maestro.app.data.local.StudyEventLocalDataSource
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.repository.AnnotationRepositoryImpl
import com.maestro.app.domain.repository.SettingsRepository
import com.maestro.app.fake.FakeDocumentRepository
import com.maestro.app.util.MainCoroutineRule
import com.maestro.app.util.TestFixtures
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var docRepo: FakeDocumentRepository
    private lateinit var viewModel: ViewerViewModel

    private val annotationRepo: AnnotationRepositoryImpl =
        mockk(relaxed = true)
    private val analyzerClient: MaterialAnalyzerClient =
        mockk(relaxed = true)
    private val settingsRepo: SettingsRepository =
        mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)
    private val extractionProgressStore =
        ExtractionProgressStore()
    private lateinit var studyEvents: StudyEventLocalDataSource
    private lateinit var quizResponses: QuizResponseLocalDataSource

    @Before
    fun setup() {
        docRepo = FakeDocumentRepository()
        io.mockk.every {
            appContext.filesDir
        } returns java.io.File(
            System.getProperty("java.io.tmpdir"),
            "maestro-test"
        )
        studyEvents = StudyEventLocalDataSource(
            java.io.File(
                System.getProperty("java.io.tmpdir"),
                "maestro-test-events-${System.nanoTime()}.json"
            )
        )
        quizResponses = QuizResponseLocalDataSource(
            java.io.File(
                System.getProperty("java.io.tmpdir"),
                "maestro-test-quiz-${System.nanoTime()}.json"
            )
        )
    }

    @After
    fun teardown() {
        io.mockk.unmockkAll()
    }

    private val mockUri: Uri = mockk(relaxed = true)

    private fun createViewModel(pdfId: String = "d1", pageCount: Int = 5): ViewerViewModel {
        return ViewerViewModel(
            annotationRepo = annotationRepo,
            analyzerClient = analyzerClient,
            settingsRepository = settingsRepo,
            documentRepository = docRepo,
            studyEvents = studyEvents,
            quizResponses = quizResponses,
            extractionProgressStore = extractionProgressStore,
            appContext = appContext,
            pdfId = pdfId,
            pageCount = pageCount,
            pdfUri = mockUri
        ).also { viewModel = it }
    }

    @Test
    fun `toggleBookmark adds page to bookmarks`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            pageCount = 5
        )
        docRepo.docs += doc

        createViewModel()
        advanceUntilIdle()

        viewModel.toggleBookmark(2)
        advanceUntilIdle()

        assertTrue(
            viewModel.bookmarkedPages.value
                .contains(2)
        )
    }

    @Test
    fun `toggleBookmark removes existing bookmark`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            pageCount = 5,
            bookmarkedPages = setOf(1, 3)
        )
        docRepo.docs += doc

        createViewModel()
        advanceUntilIdle()
        Thread.sleep(50)

        assertEquals(
            setOf(1, 3),
            viewModel.bookmarkedPages.value
        )

        viewModel.toggleBookmark(3)
        advanceUntilIdle()

        assertEquals(
            setOf(1),
            viewModel.bookmarkedPages.value
        )
    }

    @Test
    fun `bookmarkedPages loads from document`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            pageCount = 5,
            bookmarkedPages = setOf(0, 2, 4)
        )
        docRepo.docs += doc

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            setOf(0, 2, 4),
            viewModel.bookmarkedPages.value
        )
    }

    @Test
    fun `isCurrentPageBookmarked reflects state`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            pageCount = 5,
            bookmarkedPages = setOf(2)
        )
        docRepo.docs += doc

        createViewModel()
        advanceUntilIdle()

        viewModel.setCurrentPage(2)
        advanceUntilIdle()
        assertTrue(
            viewModel.isCurrentPageBookmarked.value
        )

        viewModel.setCurrentPage(0)
        advanceUntilIdle()
        assertFalse(
            viewModel.isCurrentPageBookmarked.value
        )
    }
}
