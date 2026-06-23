package com.callrecorder.app.data.model

data class UpdateDomainRequest(val domain: String)

data class MeResponse(val user: MeUser)

data class MeUser(
    val id: String,
    val name: String?,
    val email: String?,
    val role: String?,
    val domain: String?,
)