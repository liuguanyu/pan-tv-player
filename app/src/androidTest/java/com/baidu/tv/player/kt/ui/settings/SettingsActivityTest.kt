package com.baidu.tv.player.kt.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings UI 测试骨架（对应 tasks.md 7.10）。
 *
 * 说明：
 * - 全部 @Ignore，不要求本地/CI 执行，仅作为后续真机/仪器化验证的落点；
 * - 依赖 Espresso + Hilt 仪器化环境，需在真实 TV 设备或 emulator 上运行；
 * - 覆盖点：D-pad 焦点导航、切换各设置项、地点识别开关、值持久化后重建保持。
 *
 * 待办（真机验证阶段启用）：
 * 1. 引入 espresso-core / hilt-android-testing 依赖并配置 HiltTestRunner；
 * 2. 使用 ActivityScenario / ActivityScenarioRule 启动 SettingsActivity；
 * 3. 使用 Espresso pressKey(KEYCODE_DPAD_*) 模拟遥控器导航并断言焦点/文本。
 */
@Ignore("UI 测试骨架，需真机/emulator + Hilt 仪器化环境，Phase 6 不本地执行")
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    @Test
    fun dpadNavigation_movesFocusThroughRows() {
        // TODO(真机): 启动 SettingsActivity，pressKey(DPAD_DOWN) 逐行下移，
        //  断言每行获得焦点（hasFocus）。
    }

    @Test
    fun toggleShowLocation_updatesValueText() {
        // TODO(真机): 聚焦“地点识别”行，pressKey(DPAD_CENTER)，
        //  断言值文本在“开/关”间切换。
    }

    @Test
    fun cyclePlayMode_updatesValueText() {
        // TODO(真机): 聚焦“播放模式”行并确认，断言显示名循环切换。
    }

    @Test
    fun changedSettings_persistAcrossRecreate() {
        // TODO(真机): 修改设置后 recreate() Activity，
        //  断言值文本仍为修改后的值（验证 SettingsRepository 持久化）。
    }
}
