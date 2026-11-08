package com.example.aniflow.data

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecentSearchesTest {
    @Test fun newestFirstDedupedCappedAndClearable() = runTest {
        val dir = Files.createTempDirectory("recent").toFile()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val store = SettingsStore(
            ContextWrapper(null),
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "t.preferences_pb") })
        )
        try {
            store.addRecentSearch("frieren")
            store.addRecentSearch("  one piece ")
            store.addRecentSearch("x") // too short to be worth keeping
            store.addRecentSearch("FRIEREN") // same query again moves to the front, no duplicate
            assertEquals(listOf("FRIEREN", "one piece"), store.recentSearches.first())

            (1..8).forEach { store.addRecentSearch("query $it") }
            val kept = store.recentSearches.first()
            assertEquals(6, kept.size)
            assertEquals("query 8", kept.first())

            store.clearRecentSearches()
            assertEquals(emptyList<String>(), store.recentSearches.first())
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}
