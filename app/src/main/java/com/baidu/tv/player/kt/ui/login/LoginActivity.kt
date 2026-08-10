package com.baidu.tv.player.kt.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.auth.LoginUiState
import com.baidu.tv.player.kt.auth.LoginViewModel
import com.baidu.tv.player.kt.databinding.ActivityLoginBinding
import com.baidu.tv.player.kt.ui.main.MainActivity
import com.baidu.tv.player.kt.util.QRCodeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 登录界面：展示设备码二维码，轮询授权状态。
 *
 * 用 ViewBinding + [repeatOnLifecycle] 收集 [LoginViewModel.uiState]（替代 Java 版 LiveData.observe）。
 */
@AndroidEntryPoint
class LoginActivity : FragmentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRetry.setOnClickListener { viewModel.startLogin() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }

        viewModel.startLogin()
    }

    private fun render(state: LoginUiState) = when (state) {
        is LoginUiState.Loading -> {
            showLoading()
            binding.tvStatus.text = state.message
        }
        is LoginUiState.DeviceCodeReceived -> {
            showQrCode(state.response)
            binding.tvStatus.text = getString(R.string.login_scan_prompt)
        }
        LoginUiState.Polling -> Unit // 保持二维码显示
        LoginUiState.Authenticated -> {
            Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show()
            startMainActivity()
        }
        is LoginUiState.Unauthenticated -> {
            binding.tvStatus.text = state.message
            viewModel.startLogin()
        }
        is LoginUiState.Error -> {
            showError()
            binding.tvStatus.text = state.message
        }
    }

    private fun showLoading() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.llError.visibility = View.GONE
        binding.ivQrCode.visibility = View.GONE
        binding.ivQrCode.setImageDrawable(null)
    }

    private fun showQrCode(response: com.baidu.tv.player.kt.model.DeviceCodeResponse) {
        binding.pbLoading.visibility = View.GONE
        binding.llError.visibility = View.GONE
        response.fullVerificationUrl?.let { url ->
            QRCodeUtils.createQRCodeBitmap(url, QR_CODE_SIZE_PX, QR_CODE_SIZE_PX)?.let { qrCode ->
                binding.ivQrCode.setImageBitmap(qrCode)
                binding.ivQrCode.visibility = View.VISIBLE
            }
        }
    }

    private fun showError() {
        binding.pbLoading.visibility = View.GONE
        binding.llError.visibility = View.VISIBLE
        binding.ivQrCode.visibility = View.GONE
        binding.ivQrCode.setImageDrawable(null)
        // TV 遥控器场景：错误状态必须把焦点交给重试按钮，否则 OK 键无响应
        binding.btnRetry.requestFocus()
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private companion object {
        const val QR_CODE_SIZE_PX = 512
    }
}
