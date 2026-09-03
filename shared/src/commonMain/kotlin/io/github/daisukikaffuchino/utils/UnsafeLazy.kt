package io.github.daisukikaffuchino.utils

/**
 * P3：自 :app utils/CoreExtensions.kt 内联复制（jvmMain 拦截器链 / ServiceCreator 使用）。
 * :app 侧同名声明已删除，其余 :app 调用点通过同包 import 解析到本实现。
 */
fun <T> unsafeLazy(initializer: () -> T): Lazy<T> {
    return lazy(LazyThreadSafetyMode.NONE, initializer)
}
