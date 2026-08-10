package com.baidu.tv.player.kt.ui.filebrowser

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.fragment.app.FragmentActivity
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FileBrowserActivity : FragmentActivity() {

    private var fragment: FileBrowserFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)
        // 必须在 setContentView 之后调用：insetsController 依赖 DecorView，过早访问会 NPE
        enableImmersiveFullscreen()

        if (savedInstanceState == null) {
            val mediaType = intent.getIntExtra(EXTRA_MEDIA_TYPE, MediaType.ALL.value)
            val initialPath = intent.getStringExtra(EXTRA_INITIAL_PATH) ?: ROOT_PATH
            val multiSelectMode = intent.getBooleanExtra(EXTRA_MULTI_SELECT_MODE, false)
            fragment = FileBrowserFragment.newInstance(mediaType, initialPath, multiSelectMode)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, requireNotNull(fragment))
                .commit()
        } else {
            fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? FileBrowserFragment
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && fragment?.onBackPressed() == true) return true
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun enableImmersiveFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    companion object {
        const val EXTRA_MEDIA_TYPE = "mediaType"
        const val EXTRA_INITIAL_PATH = "initialPath"
        const val EXTRA_MULTI_SELECT_MODE = "multiSelectMode"
        private const val ROOT_PATH = "/"
    }
}
