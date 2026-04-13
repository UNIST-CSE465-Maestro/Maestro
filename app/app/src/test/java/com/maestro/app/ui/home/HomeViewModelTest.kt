package com.maestro.app.ui.home

import com.maestro.app.data.local.PdfMerger
import com.maestro.app.fake.FakeDocumentRepository
import com.maestro.app.util.MainCoroutineRule
import com.maestro.app.util.TestFixtures
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private val pdfMerger: PdfMerger = mockk(relaxed = true)

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(repo, pdfMerger).also {
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
    fun `deleteFolder orphans documents to root`() = runTest {
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
        assertNull(viewModel.documents.value[0].folderId)
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
}
