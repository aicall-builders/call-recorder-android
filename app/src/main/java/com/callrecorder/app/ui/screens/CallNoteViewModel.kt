package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.CallPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CallNoteUiState(
    val loading: Boolean = false,
    val memo: String = "",
    val photos: List<CallPhoto> = emptyList(),
    val uploading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class CallNoteViewModel : ViewModel() {
    private val notesRepo = CallRecorderApp.instance.container.notesRepo

    private val _state = MutableStateFlow(CallNoteUiState())
    val state: StateFlow<CallNoteUiState> = _state.asStateFlow()

    private var currentCallId: String? = null

    /** 화면 진입 시 호출 */
    fun load(callId: String) {
        currentCallId = callId
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            notesRepo.getNote(callId).fold(
                onSuccess = { res ->
                    _state.value = _state.value.copy(
                        loading = false,
                        memo = res.memo,
                        photos = res.photos,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    /** 메모 입력값 즉시 반영 (저장은 별도) */
    fun onMemoChange(text: String) {
        _state.value = _state.value.copy(memo = text)
    }

    /** 메모 서버 저장 */
    fun saveMemo() {
        val callId = currentCallId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            notesRepo.updateMemo(callId, _state.value.memo).fold(
                onSuccess = {
                    _state.value = _state.value.copy(saving = false, message = "메모가 저장되었어요")
                },
                onFailure = {
                    _state.value = _state.value.copy(saving = false, error = it.message)
                },
            )
        }
    }

    /** 사진 업로드 (압축된 바이트) */
    fun uploadPhoto(fileName: String, bytes: ByteArray) {
        val callId = currentCallId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(uploading = true, error = null)
            notesRepo.uploadPhoto(callId, fileName, bytes).fold(
                onSuccess = { photo ->
                    _state.value = _state.value.copy(
                        uploading = false,
                        photos = _state.value.photos + photo,
                        message = "사진이 추가되었어요",
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(uploading = false, error = it.message)
                },
            )
        }
    }

    /** 사진 삭제 */
    fun deletePhoto(photoId: String) {
        val callId = currentCallId ?: return
        viewModelScope.launch {
            notesRepo.deletePhoto(callId, photoId).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        photos = _state.value.photos.filterNot { it.id == photoId },
                        message = "사진이 삭제되었어요",
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(error = it.message)
                },
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}