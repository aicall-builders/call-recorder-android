package com.callrecorder.app.data.repository

import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.model.CustomKeyword
import com.callrecorder.app.data.model.CreateKeywordRequest
import com.callrecorder.app.data.model.UpdateKeywordRequest

class KeywordRepository(private val api: ApiService) {

    suspend fun listKeywords(storeId: String): Result<List<CustomKeyword>> = runCatching {
        api.listKeywords(storeId).keywords
    }

    suspend fun createKeyword(
        storeId: String,
        keyword: String,
        label: String? = null,
        actionRequired: Boolean = true,
    ): Result<CustomKeyword> = runCatching {
        api.createKeyword(
            storeId,
            CreateKeywordRequest(keyword = keyword, label = label, actionRequired = actionRequired)
        )
    }

    suspend fun updateKeyword(storeId: String, keywordId: String, isEnabled: Boolean): Result<Unit> = runCatching {
        api.updateKeyword(storeId, keywordId, UpdateKeywordRequest(isEnabled = isEnabled))
    }

    suspend fun deleteKeyword(storeId: String, keywordId: String): Result<Unit> = runCatching {
        api.deleteKeyword(storeId, keywordId)
    }
}