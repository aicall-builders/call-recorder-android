package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.worker.UploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingApprovalUiState(
    val recordings: List<RecordingEntity> = emptyList(),
    val duplicateIds: Set<Long> = emptySet(),
    val loading: Boolean = false,
)

class PendingApprovalViewModel : ViewModel() {
    private val recordingDao = CallRecorderApp.instance.container.recordingDao

    private val _state = MutableStateFlow(PendingApprovalUiState())
    val state: StateFlow<PendingApprovalUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val recordings = recordingDao.getAwaitingApproval()
            val duplicateIds = recordings
                .filter { recordingDao.countActiveByFileNameAndSize(it.fileName, it.fileSize) > 1 }
                .map { it.id }
                .toSet()
            _state.value = PendingApprovalUiState(
                recordings = recordings,
                duplicateIds = duplicateIds,
                loading = false,
            )
        }
    }

    fun approveOne(id: Long) {
        viewModelScope.launch {
            recordingDao.approveOne(id)
            triggerUpload()
            load()
        }
    }

    fun rejectOne(id: Long) {
        viewModelScope.launch {
            recordingDao.deleteOne(id)
            load()
        }
    }

    fun approveAll() {
        viewModelScope.launch {
            recordingDao.approveAll()
            triggerUpload()
            load()
        }
    }

    private fun triggerUpload() {
        UploadWorker.enqueueOneShot(CallRecorderApp.instance.applicationContext)
    }
}
