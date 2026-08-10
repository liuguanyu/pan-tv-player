package com.baidu.tv.player.kt.ui.filebrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Phase 4 UI 测试骨架：待 Phase 5+ 补齐 Hilt fake repository 与 ActivityScenario 验证焦点链。")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FileBrowserFragmentFocusTest {

    @Test
    fun directoryList_receivesFocusAfterLoad() {
        // 骨架：启动 FileBrowserActivity，注入 fake FileRepository 返回非空列表，断言首个 item 获得焦点。
    }

    @Test
    fun backKey_returnsToParentDirectory() {
        // 骨架：进入子目录后发送 BACK 键，断言 currentPath 回到父目录且 RecyclerView 焦点仍可达。
    }
}
