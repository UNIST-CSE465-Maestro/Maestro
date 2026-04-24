package com.maestro.app.ui.home

import android.content.Context
import android.net.Uri
import com.maestro.app.data.local.PdfMerger
import com.maestro.app.data.remote.AnalysisTaskResponse
import com.maestro.app.data.remote.MaterialAnalyzerClient
import com.maestro.app.data.remote.MaterialAnalyzerHash
import com.maestro.app.domain.model.ExtractionStatus
import com.maestro.app.fake.FakeDocumentRepository
import com.maestro.app.util.MainCoroutineRule
import com.maestro.app.util.TestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var repo: FakeDocumentRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        repo = FakeDocumentRepository()
    }

    @After
    fun teardown() {
        // Allow IO threads to settle before cleanup
        Thread.sleep(50)
        unmockkAll()
    }

    private val pdfMerger: PdfMerger = mockk(relaxed = true)
    private val analyzerClient: MaterialAnalyzerClient =
        mockk(relaxed = true)
    private val appContext: Context =
        mockk(relaxed = true)

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            repo,
            pdfMerger,
            analyzerClient,
            appContext
        ).also {
            viewModel = it
        }
    }

    @Test
    fun `init loads documents and folders`() = runTest {
        val doc = TestFixtures.pdfDocument(id = "d1")
        val folder = TestFixtures.folder(id = "f1")
        repo.docs += doc
        repo.folders += folder

        createViewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.documents.value.size)
        assertEquals("d1", viewModel.documents.value[0].id)
        assertEquals(1, viewModel.folders.value.size)
        assertEquals("f1", viewModel.folders.value[0].id)
    }

    @Test
    fun `createFolder adds folder to state`() = runTest {
        createViewModel()
        advanceUntilIdle()

        viewModel.createFolder("New Folder")
        advanceUntilIdle()

        assertEquals(1, viewModel.folders.value.size)
        assertEquals(
            "New Folder",
            viewModel.folders.value[0].name
        )
    }

    @Test
    fun `deleteDocument removes from state`() = runTest {
        val doc = TestFixtures.pdfDocument(id = "d1")
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.documents.value.size)

        viewModel.deleteDocument("d1")
        advanceUntilIdle()

        assertTrue(viewModel.documents.value.isEmpty())
    }

    @Test
    fun `renameDocument updates name`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            displayName = "old.pdf"
        )
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        viewModel.renameDocument("d1", "new.pdf")
        advanceUntilIdle()

        assertEquals(
            "new.pdf",
            viewModel.documents.value[0].displayName
        )
    }

    @Test
    fun `navigateFolder changes currentFolderId`() = runTest {
        createViewModel()
        advanceUntilIdle()

        assertNull(viewModel.currentFolderId.value)

        viewModel.navigateFolder("f1")

        assertEquals("f1", viewModel.currentFolderId.value)
    }

    @Test
    fun `deleteFolder deletes non-empty folder with contents`() = runTest {
        val folder = TestFixtures.folder(id = "f1")
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            folderId = "f1"
        )
        repo.folders += folder
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        viewModel.deleteFolder("f1")
        advanceUntilIdle()

        assertTrue(viewModel.folders.value.isEmpty())
        assertTrue(viewModel.documents.value.isEmpty())
    }

    @Test
    fun `deleteFolder works for empty folder`() = runTest {
        val folder = TestFixtures.folder(id = "f1")
        repo.folders += folder

        createViewModel()
        advanceUntilIdle()

        viewModel.deleteFolder("f1")
        advanceUntilIdle()

        assertTrue(viewModel.folders.value.isEmpty())
    }

    @Test
    fun `togglePin pins unpinned document`() = runTest {
        val doc = TestFixtures.pdfDocument(id = "d1")
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.documents.value[0].isPinned)

        viewModel.togglePin("d1")
        advanceUntilIdle()

        assertTrue(viewModel.documents.value[0].isPinned)
    }

    @Test
    fun `togglePin unpins pinned document`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            isPinned = true
        )
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.documents.value[0].isPinned)

        viewModel.togglePin("d1")
        advanceUntilIdle()

        assertFalse(viewModel.documents.value[0].isPinned)
    }

    @Test
    fun `pinned documents appear before unpinned`() = runTest {
        val unpinned = TestFixtures.pdfDocument(
            id = "d1",
            displayName = "unpinned.pdf",
            addedTimestamp = 2000L
        )
        val pinned = TestFixtures.pdfDocument(
            id = "d2",
            displayName = "pinned.pdf",
            isPinned = true,
            addedTimestamp = 1000L
        )
        repo.docs += unpinned
        repo.docs += pinned

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            "d2",
            viewModel.documents.value[0].id
        )
        assertEquals(
            "d1",
            viewModel.documents.value[1].id
        )
    }

    @Test
    fun `moveDocument changes folder`() = runTest {
        val doc = TestFixtures.pdfDocument(id = "d1")
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        viewModel.moveDocument("d1", "f2")
        advanceUntilIdle()

        assertEquals(
            "f2",
            viewModel.documents.value[0].folderId
        )
    }

    private fun setupExtractionMocks() {
        mockkStatic(Uri::class)
        every {
            Uri.parse(any())
        } returns mockk(relaxed = true)
        mockkObject(MaterialAnalyzerHash)
        coEvery {
            MaterialAnalyzerHash.compute(
                any(),
                any(),
                any()
            )
        } returns "fakehash"
        every {
            appContext.filesDir
        } returns File(
            System.getProperty("java.io.tmpdir"),
            "maestro-test"
        )
    }

    @Test
    fun `init resumes EXTRACTING documents`() = runTest {
        setupExtractionMocks()
        val taskResp = AnalysisTaskResponse(
            id = "task-1",
            status = "completed"
        )
        coEvery {
            analyzerClient.upload(any(), any(), any())
        } returns taskResp
        coEvery {
            analyzerClient.pollUntilComplete(any())
        } returns taskResp
        coEvery {
            analyzerClient.getResultMd(any())
        } returns "# Result"
        coEvery {
            analyzerClient.getResultJson(any())
        } returns "{}"

        val doc = TestFixtures.pdfDocument(
            id = "d1",
            extractionStatus = ExtractionStatus.EXTRACTING,
            extractionMode = "standard"
        )
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        coVerify {
            analyzerClient.upload(
                any(),
                "standard",
                any()
            )
        }
    }

    @Test
    fun `init ignores EXTRACTING without mode`() = runTest {
        val doc = TestFixtures.pdfDocument(
            id = "d1",
            extractionStatus =
            ExtractionStatus.EXTRACTING,
            extractionMode = null
        )
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) {
            analyzerClient.upload(
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `extraction failure sets FAILED status`() = runTest {
        setupExtractionMocks()
        coEvery {
            analyzerClient.upload(
                any(),
                any(),
                any()
            )
        } throws RuntimeException("server down")

        val doc = TestFixtures.pdfDocument(
            id = "d1",
            extractionStatus =
            ExtractionStatus.EXTRACTING,
            extractionMode = "ai"
        )
        repo.docs += doc

        createViewModel()
        advanceUntilIdle()

        assertEquals(
            ExtractionStatus.FAILED,
            repo.docs.find { it.id == "d1" }
                ?.extractionStatus
        )
    }
}
