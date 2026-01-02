package com.mari.aksa.page.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class DictionaryViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DictionaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DictionaryViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

