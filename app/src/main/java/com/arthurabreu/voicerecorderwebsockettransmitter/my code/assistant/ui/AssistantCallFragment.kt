package com.mercantil.assistant.ui

import android.media.AudioManager
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.navigation.fragment.findNavController
import com.mercantil.assistant.actionEvent.AssistantCallEvent
import com.mercantil.assistant.data.response.AssistantCallResponse
import com.mercantil.assistant.viewmodel.AssistantCallViewModel
import com.mercantil.commons.R
import com.mercantil.commons.util.PermissionUtil
import com.mercantil.commons.util.SharedPreferencesUtil
import com.mercantil.commons.util.UXCamUtil
import com.mercantil.constants.CommonUxCamConstants
import com.mercantil.core.data.BaseResponse
import com.mercantil.core.data.MenuItem
import com.mercantil.core.data.RequestType
import com.mercantil.core.ui.BaseFragmentCompose
import com.mercantil.design_system.compose.screens.AssistantCallScreen
import com.mercantil.design_system.util.mbMenuArgs
import com.mercantil.navigation.Navigate
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.mercantil.core.websockets.util.AssistantWsLog

class AssistantCallFragment : BaseFragmentCompose<AssistantCallResponse>() {

    private val viewModel: AssistantCallViewModel by viewModel()
    private val menu by mbMenuArgs<MenuItem>()

    @Composable
    override fun SetLayout() {
        viewModel.assistantState.value.let { data ->
            AssistantCallScreen(
                data,
                onBack = {
                    popBackToHireLoanList()
                } ,
                onFinish = {
                    UXCamUtil.sendEvent(CommonUxCamConstants.ASSISTANT_CALL_BOTAO_FINALIZAR)
                    popBackToHireLoanList()
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().volumeControlStream = AudioManager.STREAM_MUSIC
        val payloadIa = menu?.payloadAssistenteIa
        if (!payloadIa.isNullOrBlank()) {
            AssistantWsLog.d(this, "onViewCreated", "recebeu PayloadAssistenteIa (len=%s)", payloadIa.length)
            viewModel.setPayloadAssistenteIa(payloadIa)
        } else {
            AssistantWsLog.d(this, "onViewCreated", "nenhum PayloadAssistenteIa recebido no menu")
        }
        ensureMicPermission()
        observeAssistantEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopStreaming()
    }

    override fun getBaseViewModel() = viewModel

    override fun getMenuItem() = menu

    private fun popBackToHireLoanList() {
        viewModel.stopStreaming()
        findNavController().popBackStack(R.id.HireLoanListFragmentV2, false)
    }

    private fun observeAssistantEvents() {
        viewModel.events.observe(viewLifecycleOwner) { event ->
            if (event == null) return@observe
            when (event) {
                is AssistantCallEvent.NavigateBack -> {
                    popBackToHireLoanList()
                }
                is AssistantCallEvent.ExecutePushDeeplink -> {
                    viewModel.stopStreaming()
                    val entradaJson = event.entrada
                    AssistantWsLog.i(this, "observeAssistantEvents", "AssistantCallEvent.ExecutePushDeeplink → navigating with entradaJson=%s", entradaJson)
                    SharedPreferencesUtil.setCameFromAssistantToLoan(true)
                    Navigate.deepLinkFromAssistant(
                        activity = requireActivity(),
                        navController = findNavController(),
                        deeplinkNavigation = entradaJson,
                        menuItem = menu,
                        onReturnError = { showErrorDialog(it, requestType = RequestType.EXEC) },
                        onReturnNullParameters = {}
                    )
                }
                is AssistantCallEvent.ShowError -> {
                    viewModel.stopStreaming()
                    val error = BaseResponse().apply {
                        indicadorErro = 1
                        descricaoErro = event.message
                        descricaoErroMobile = event.message
                        tipoMensagem = 3
                    }
                    showErrorDialog(error, requestType = RequestType.EXEC)
                }
            }
        }
    }

    private fun ensureMicPermission() {
        if (SharedPreferencesUtil.getCameFromAssistantToLoan()) {
            AssistantWsLog.d(this, "ensureMicPermission", "already came from assistant flow, skipping auto-start")
            return
        }
        val ctx = requireContext()
        if (PermissionUtil.hasMicrophonePermission(ctx)) {
            viewModel.updateMicPermission(true)
            viewModel.startStreaming()
        } else {
            PermissionUtil.requestMicrophonePermission(ctx) { status ->
                val granted = status == PermissionUtil.PermissionStatus.GRANTED
                viewModel.updateMicPermission(granted)
                if (granted) {
                    viewModel.startStreaming()
                }
            }
        }
    }
}