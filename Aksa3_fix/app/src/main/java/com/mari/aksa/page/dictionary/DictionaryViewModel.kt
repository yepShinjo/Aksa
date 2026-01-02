package com.mari.aksa.page.dictionary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DictionaryViewModel : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _aksaraList = MutableStateFlow<List<KamusResponseItem>>(emptyList())
    val aksaraList: StateFlow<List<KamusResponseItem>> = _aksaraList

    private val _language = MutableStateFlow("id")
    val language: StateFlow<String> = _language

    // master list (semua data kamus dari JSON, tanpa filter)
    private var masterList: List<KamusResponseItem> = emptyList()

    fun checkAndLoadInitialData(context: Context) {
        // supaya tidak load ulang terus
        if (masterList.isNotEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true

            val json = context.assets.open("aksara_bali_data.json")
                .bufferedReader().use { it.readText() }

            masterList = Gson().fromJson(
                json,
                object : TypeToken<List<KamusResponseItem>>() {}.type
            )

            _aksaraList.value = masterList
            _isLoading.value = false
        }
    }

    fun setQuery(newQuery: String) {
        _query.value = newQuery

        viewModelScope.launch {
            val q = newQuery.trim()

            _aksaraList.value =
                if (q.isEmpty()) {
                    masterList
                } else {
                    masterList.filter { item ->
                        item.latin.contains(q, ignoreCase = true) ||
                                item.description_id.contains(q, ignoreCase = true) ||
                                item.description_en.contains(q, ignoreCase = true)
                    }
                }
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
    }
}
