package lovehan1me.feature.home.artist

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 作者页弱守卫：`load()` 绕过 `if (loading) return`、不 cancel、不校验 userId，
 * 快速切作者会把两家的页并进 `_works`。
 *
 * ViewModel 本体依赖 `NetworkRepo` 单例，headless 起不来；这里钉抽出的纯谓词
 * [shouldApplyArtistResponse]（与 `shouldApplySearchResponse` 同手法），
 * 生产路径 `load()` / `loadMoreInternal()` 的收集分支直接调用它。
 */
class ArtistStaleGuardTest {

    @Test
    fun `同作者响应放行_切走后旧响应丢弃`() {
        assertTrue(
            shouldApplyArtistResponse("42", "42"),
            "同作者响应必须放行",
        )
        assertFalse(
            shouldApplyArtistResponse("41", "42"),
            "切作者后旧家响应必须丢弃，否则两家列表互串",
        )
    }

    @Test
    fun `未加载过时旧响应丢弃`() {
        assertFalse(
            shouldApplyArtistResponse("41", null),
            "loadedUserId 为空时任何响应都不该并入",
        )
    }
}
