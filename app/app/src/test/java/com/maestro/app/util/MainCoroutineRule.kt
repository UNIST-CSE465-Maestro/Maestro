package com.maestro.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit 4 Rule that swaps Dispatchers.Main with a TestDispatcher.
 * Use in any test that touches ViewModels or coroutines on Main.
 *
 * ```
 * @get:Rule val coroutineRule = MainCoroutineRule()
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainCoroutineRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
