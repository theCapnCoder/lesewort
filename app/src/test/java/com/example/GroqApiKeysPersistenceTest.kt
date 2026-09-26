package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.GroqApiKeyItem
import com.example.ui.MainViewModel
import com.example.ui.DEFAULT_GROQ_API_KEY
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroqApiKeysPersistenceTest {

    private fun createViewModel(context: Context): MainViewModel {
        val db = com.example.data.db.AppDatabase.getDatabase(context)
        val repo = com.example.data.repository.BookRepository(
            db.knownWordDao(),
            db.bookDao(),
            db.chapterDao(),
            db.studyWordDao()
        )
        return MainViewModel(repo)
    }

    @Test
    fun `test initial settings loads default key when empty`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("lesewort_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val viewModel = createViewModel(context)
        viewModel.initSettings(context)

        val keys = viewModel.groqApiKeys.value
        assertEquals(1, keys.size)
        assertEquals(DEFAULT_GROQ_API_KEY, keys.first().key)
        assertEquals(keys.first().id, viewModel.selectedGroqKeyId.value)
        assertEquals(DEFAULT_GROQ_API_KEY, viewModel.groqApiKey.value)
    }

    @Test
    fun `test add and select new key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("lesewort_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val viewModel = createViewModel(context)
        viewModel.initSettings(context)

        // Add second key and select it
        viewModel.addGroqApiKey(context, "Второй ключ", "gsk_custom_second_key_12345678", selectImmediately = true)

        val updatedKeys = viewModel.groqApiKeys.value
        assertEquals(2, updatedKeys.size)
        val secondKey = updatedKeys.find { it.name == "Второй ключ" }
        assertNotNull(secondKey)
        assertEquals(secondKey!!.id, viewModel.selectedGroqKeyId.value)
        assertEquals("gsk_custom_second_key_12345678", viewModel.groqApiKey.value)

        // Verify stored in SharedPreferences
        val savedSelectedId = prefs.getString("groq_selected_key_id", null)
        assertEquals(secondKey.id, savedSelectedId)
        val savedApiKey = prefs.getString("groq_api_key", null)
        assertEquals("gsk_custom_second_key_12345678", savedApiKey)

        // Switch back to first key
        val firstKey = updatedKeys.first()
        viewModel.selectGroqApiKey(context, firstKey.id)
        assertEquals(firstKey.id, viewModel.selectedGroqKeyId.value)
        assertEquals(firstKey.key, viewModel.groqApiKey.value)
        assertEquals(firstKey.id, prefs.getString("groq_selected_key_id", null))
    }

    @Test
    fun `test masked key formatting`() {
        val item = GroqApiKeyItem(id = "1", name = "Test", key = "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")
        assertTrue(item.maskedKey.startsWith("gsk_4TYC"))
        assertTrue(item.maskedKey.endsWith("qXwO"))
        assertTrue(item.maskedKey.contains("••••"))
    }
}
