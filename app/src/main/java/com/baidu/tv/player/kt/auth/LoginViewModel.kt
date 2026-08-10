package com.baidu.tv.player.kt.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.tv.player.kt.model.AuthInfo
import com.baidu.tv.player.kt.model.DeviceCodeResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录页 ViewModel。
 *
 * 通过 [StateFlow] 暴露 [LoginUiState]，协程轮询 device_code 状态（替代 Java 版 Handler.postDelayed）。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authService: BaiduAuthService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading("正在初始化..."))
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** 连续自动刷新二维码的次数（防止无限循环）。 */
    private var autoRefreshCount = 0

    /** 开始登录流程（用户主动触发）：重置自动刷新计数后获取设备码 → 展示二维码 → 轮询。 */
    fun startLogin() {
        autoRefreshCount = 0
        doStartLogin()
    }

    private fun doStartLogin() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading("正在获取设备码...")
            try {
                val deviceCode = authService.getDeviceCode()
                _uiState.value = LoginUiState.DeviceCodeReceived(deviceCode)
                _uiState.value = LoginUiState.Polling
                poll(deviceCode.deviceCode!!)
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "获取设备码失败")
            }
        }
    }

    /** 轮询 device_code 状态直到认证成功 / 过期 / 超时。过期时自动刷新二维码重新开始（有上限）。 */
    private suspend fun poll(deviceCode: String) {
        when (authService.pollUntilAuthenticated(deviceCode)) {
            BaiduAuthService.PollResult.AUTHENTICATED ->
                _uiState.value = LoginUiState.Authenticated
            BaiduAuthService.PollResult.EXPIRED -> {
                if (autoRefreshCount < MAX_AUTO_REFRESH) {
                    // 二维码过期：自动获取新设备码刷新二维码
                    autoRefreshCount++
                    doStartLogin()
                } else {
                    _uiState.value = LoginUiState.Error("二维码已过期，请重试")
                }
            }
            else ->
                _uiState.value = LoginUiState.Error("授权失败，请重试")
        }
    }

    /** 退出登录。 */
    fun logout() {
        viewModelScope.launch { authService.logout() }
        _uiState.value = LoginUiState.Unauthenticated("已退出登录")
    }

    /** 检查是否已认证（用于启动路由）。 */
    fun isAuthenticated(): Boolean = authService.isAuthenticated()

    /** 获取认证信息。 */
    fun getAuthInfo(): AuthInfo = authService.getAuthInfo()

    private companion object {
        /** 二维码过期后最大自动刷新次数（每轮约 5 分钟，约可持续 2 小时）。 */
        const val MAX_AUTO_REFRESH = 24
    }
}

/** 登录页 UI 状态。 */
sealed interface LoginUiState {
    data class Loading(val message: String) : LoginUiState
    data class DeviceCodeReceived(val response: DeviceCodeResponse) : LoginUiState
    data object Polling : LoginUiState
    data object Authenticated : LoginUiState
    data class Unauthenticated(val message: String) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
