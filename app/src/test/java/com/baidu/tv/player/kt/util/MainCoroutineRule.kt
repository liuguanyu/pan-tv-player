package com.baidu.tv.player.kt.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit Rule：为 ViewModel 单测提供受控的 [TestDispatcher] 与 [TestScope]，
 * 替换 `Dispatchers.Main`。用 [TestScope.advanceUntilIdle] 或 `advanceTimeBy` 推进协程。
 *
 * 默认用 [UnconfinedTestDispatcher]：launch 的协程立即执行至第一个挂起点（如 delay），
 * 便于顺序断言；delay 仍受 TestScope 控制器时钟约束。
 *
 * 放在 `src/test`（纯 JVM 单测）与 `src/androidTest`（集成测试）中各一份以共享使用。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainCoroutineRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    val scope: TestScope = TestScope(dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
