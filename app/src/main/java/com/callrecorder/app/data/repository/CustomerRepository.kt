package com.callrecorder.app.data.repository

import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.model.CreateConsentLinkRequest
import com.callrecorder.app.data.model.CreateCustomerMemoRequest
import com.callrecorder.app.data.model.CustomerHistoryItem
import com.callrecorder.app.data.model.CustomerListItem
import com.callrecorder.app.data.model.CustomerMemoPhotoUploadUrlRequest
import com.callrecorder.app.data.model.CustomerProfileResponse
import com.callrecorder.app.data.model.SaveCustomerMemoPhotoRequest
import com.callrecorder.app.data.model.UpdateCustomerRequest

class CustomerRepository(
    private val api: ApiService,
) {
    suspend fun listCustomers(): Result<List<CustomerListItem>> = runCatching {
        api.listCustomers().customers
    }

    suspend fun getCustomer(phone: String): Result<CustomerProfileResponse> = runCatching {
        api.getCustomer(phone)
    }

    suspend fun updateCustomer(phone: String, body: UpdateCustomerRequest): Result<Unit> = runCatching {
        val response = api.updateCustomer(phone, body)
        if (!response.isSuccessful) error("updateCustomer failed: HTTP ${response.code()}")
        Unit
    }

    suspend fun createConsentLink(
        phone: String,
        name: String? = null,
        customerName: String? = null,
        storeId: String? = null,
    ) = runCatching {
        api.createCustomerConsentLink(
            phone,
            CreateConsentLinkRequest(
                name = name,
                customerName = customerName,
                storeId = storeId,
            ),
        )
    }

    suspend fun getHistory(phone: String): Result<List<CustomerHistoryItem>> = runCatching {
        api.getCustomerHistory(phone).items
    }

    suspend fun createMemo(
        phone: String,
        memo: String,
        isAnonymized: Boolean = false,
    ) = runCatching {
        api.createCustomerMemo(
            phone,
            CreateCustomerMemoRequest(
                memo = memo,
                isAnonymized = isAnonymized,
            ),
        )
    }

    suspend fun requestMemoPhotoUploadUrl(
        phone: String,
        memoId: String,
        fileName: String,
    ) = runCatching {
        api.requestCustomerMemoPhotoUploadUrl(
            phone = phone,
            memoId = memoId,
            body = CustomerMemoPhotoUploadUrlRequest(fileName),
        )
    }

    suspend fun saveMemoPhoto(
        phone: String,
        memoId: String,
        photoId: String?,
        s3Key: String,
        caption: String? = null,
    ) = runCatching {
        api.saveCustomerMemoPhoto(
            phone = phone,
            memoId = memoId,
            body = SaveCustomerMemoPhotoRequest(
                photoId = photoId,
                s3Key = s3Key,
                caption = caption,
            ),
        )
    }
}
