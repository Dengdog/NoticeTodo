package com.dengao.noticetodo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AppViewModel(application: Application) : AndroidViewModel(application) {

    /** null 表示还在读 DataStore，避免已引导过的用户闪一下引导页。 */
    val onboarded: StateFlow<Boolean?> = (application as NoticeTodoApp)
        .container
        .settingsRepository
        .onboarded
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
