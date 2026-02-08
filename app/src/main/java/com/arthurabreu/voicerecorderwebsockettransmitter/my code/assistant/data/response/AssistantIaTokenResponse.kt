package com.mercantil.assistant.data.response

import com.google.gson.annotations.SerializedName
import com.mercantil.core.data.BaseResponse

data class AssistantIaTokenResponse(
    @SerializedName("IdTokenGoogleIa")
    val token: String?,
) : BaseResponse()