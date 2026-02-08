package com.mercantil.assistant.data.response

import com.mercantil.core.data.BaseResponse
import com.google.gson.annotations.SerializedName
import com.mercantil.core.data.ItemUI

data class AssistantCallResponse(
    @SerializedName("Titulo")
    val title: String = "",
    @SerializedName("RespondendoOuvindo")
    val respondendoOuvindo: String = "",
    @SerializedName("MBMensagem")
    val mbMessage: ItemUI<String> = ItemUI(),
    @SerializedName("MBBButton")
    val mbButton: ItemUI<String> = ItemUI(),
    @SerializedName("EmprestimosAtivos")
    val emprestimosAtivos: String = ""
) : BaseResponse()