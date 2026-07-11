package com.callrecorder.app.data.repository

import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.local.TokenStore
import com.callrecorder.app.data.model.CreateStoreRequest
import com.callrecorder.app.data.model.Store

class StoreRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    suspend fun list(): Result<List<Store>> = runCatching { api.listStores().stores }

    suspend fun create(name: String, category: String, phone: String?, address: String?): Result<Store> =
        runCatching {
            val s = api.createStore(CreateStoreRequest(name, category, phone, address))
            // 첫 가게라면 활성 가게로 설정
            if (tokenStore.getActiveStore() == null) tokenStore.setActiveStore(s.id)
            s
        }

    suspend fun setActive(storeId: String) = tokenStore.setActiveStore(storeId)
    suspend fun activeStoreId(): String? = tokenStore.getActiveStore()

    /**
     * 백업/재설치 과정에서 예전 계정의 active_store_id가 복원될 수 있어
     * 서버가 내려준 현재 계정의 매장 목록과 반드시 대조한다.
     */
    suspend fun ensureActiveStoreId(): Result<String?> = runCatching {
        val stores = api.listStores().stores
        val current = tokenStore.getActiveStore()
        val validCurrent = stores.firstOrNull { it.id == current }
        if (validCurrent != null) return@runCatching validCurrent.id

        val first = stores.firstOrNull()
        if (first != null) {
            tokenStore.setActiveStore(first.id)
            return@runCatching first.id
        }

        val created = api.createStore(CreateStoreRequest("내 가게", "기타", null, null))
        tokenStore.setActiveStore(created.id)
        created.id
    }
}
