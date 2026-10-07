package lovehan1me.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * `SettingsRepository.install` 可重复安装的回归守卫。
 *
 * 钉三条不变式：
 * ① **可重复安装** —— 同 JVM 内第二次 install 不再抛。早先实现是
 *    `check(!::store.isInitialized)`（全局只许成功一次），测试侧只能写成
 *    `runCatching { install(...) }` 静默吞异常 —— 于是"我以为装上了自己的 store、
 *    实际用的是上一个测试留下的那个"这种错误前提不会被发现，测试在测别的东西而不报错。
 * ② **`settings` / `current` 立即切到新 store**。
 * ③ **派生流随换代重建** —— 这条最关键。派生流是 `by lazy` + `stateIn`，一旦求值就记住
 *    求值当时那个 store 的 upstream。实现上必须把它们挂在**每代 Session** 里；若将来有人
 *    挪回 `SettingsRepository` 自身（或改回外层的 `by lazy`），替换 store 后会继续读
 *    旧 store 的残影，本用例必须红。
 *
 * 断言用 `.value` 而非 `first()`：`stateIn(Eagerly, initial)` 的初值就取自当次
 * `store.settings.value`，因此"刚 install 完、上游还没发第二个值"这一刻读 `.value`
 * 是确定性的，不会 flaky。
 */
class SettingsRepositoryReinstallTest {

    private class FakeStore(initial: AppSettings = AppSettings()) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }

        fun set(value: AppSettings) {
            state.value = value
        }
    }

    /**
     * 用完把全局复位成中性默认值。
     *
     * 这是 `install` 可重复之后才做得到的事 —— 旧实现下"复位"无从谈起，
     * 只能让第一个装上 store 的测试决定后面所有测试看到的设置。
     */
    @AfterTest
    fun 复位为默认设置() {
        SettingsRepository.install(FakeStore(AppSettings()))
    }

    @Test
    fun `可重复安装_第二次不抛且current立即切换`() {
        SettingsRepository.install(FakeStore(AppSettings(isAlreadyLogin = false)))
        assertEquals(false, SettingsRepository.current.isAlreadyLogin)

        SettingsRepository.install(FakeStore(AppSettings(isAlreadyLogin = true)))
        assertEquals(true, SettingsRepository.current.isAlreadyLogin)
    }

    @Test
    fun `settings直接指向新store`() {
        val a = FakeStore()
        val b = FakeStore()

        SettingsRepository.install(a)
        assertSame(a.settings, SettingsRepository.settings)

        SettingsRepository.install(b)
        assertSame(b.settings, SettingsRepository.settings)
    }

    @Test
    fun `派生流随换代重建_不残留上一份store`() {
        val a = FakeStore(AppSettings(isAlreadyLogin = false))
        val b = FakeStore(AppSettings(isAlreadyLogin = true))

        SettingsRepository.install(a)
        // 先求值一次，把 a 的派生流钉进 lazy —— 旧实现正是卡在这里
        assertEquals(false, SettingsRepository.loginStateFlow.value)

        SettingsRepository.install(b)
        assertEquals(true, SettingsRepository.loginStateFlow.value, "替换 store 后读到了登录态残影")
    }

    @Test
    fun `替换后旧store的写入不再影响派生流`() {
        val a = FakeStore(AppSettings(isAlreadyLogin = false))
        SettingsRepository.install(a)
        assertEquals(false, SettingsRepository.loginStateFlow.value)

        SettingsRepository.install(FakeStore(AppSettings(isAlreadyLogin = true)))
        a.set(AppSettings(isAlreadyLogin = false))

        assertEquals(true, SettingsRepository.loginStateFlow.value, "旧代次的 store 仍在发射")
    }
}
