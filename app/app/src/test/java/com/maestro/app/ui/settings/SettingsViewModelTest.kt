package com.maestro.app.ui.settings

import app.cash.turbine.test
import com.maestro.app.domain.model.ChatMessage
import com.maestro.app.domain.service.LlmService
import com.maestro.app.fake.FakeSettingsRepository
import com.maestro.app.util.MainCoroutineRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var settingsRepo: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    /** Stub LlmService that returns predetermined validation results */
    private var nextValidationResult = true

    private val stubLlmService = object : LlmService {
        override fun stream(
            messages: List<ChatMessage>,
            systemPrompt: String?,
            images: List<ByteArray>
        ): Flow<String> = emptyFlow()

        override suspend fun complete(
            messages: List<ChatMessage>,
            systemPrompt: String?,
            images: List<ByteArray>
        ): String = ""

        override suspend fun validateApiKey(apiKey: String): Boolean = nextValidationResult
    }

    @Before
    fun setup() {
        settingsRepo = FakeSettingsRepository()
        viewModel = SettingsViewModel(
            settingsRepo,
            stubLlmService,
            mockk(relaxed = true)
        )
    }

    @Test
    fun `initially api key is not set`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.apiKeySet.value)
    }

    @Test
    fun `saveApiKey updates state to set`() = runTest {
        viewModel.saveApiKey("sk-test-123")
        advanceUntilIdle()

        assertTrue(viewModel.apiKeySet.value)
    }

    @Test
    fun `clearApiKey updates state to not set`() = runTest {
        viewModel.saveApiKey("sk-test-123")
        advanceUntilIdle()

        viewModel.clearApiKey()
        advanceUntilIdle()

        assertFalse(viewModel.apiKeySet.value)
    }

    @Test
    fun `validateApiKey shows success`() = runTest {
        nextValidationResult = true

        viewModel.validateApiKey("sk-valid")
        advanceUntilIdle()

        viewModel.validationResult.test {
            val result = awaitItem()
            assertTrue(result?.contains("OK") == true)
        }
    }

    @Test
    fun `validateApiKey shows failure`() = runTest {
        nextValidationResult = false

        viewModel.validateApiKey("sk-invalid")
        advanceUntilIdle()

        viewModel.validationResult.test {
            val result = awaitItem()
            assertTrue(
                result?.contains("유효하지") == true
            )
        }
    }
}
