package lovehan1me.core.util

/**
 * P4：自 :app utils/CoreExtensions.kt 下沉（HanimeVideo.MyList.titleArray 使用）。
 * :app 侧原文件已删除，无其他调用点。
 */
inline fun <I, reified O> List<I>.mapToArray(transform: (I) -> O): Array<O> {
    return Array(size) { index -> transform(this[index]) }
}
