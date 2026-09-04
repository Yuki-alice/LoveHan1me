package io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage

/**
 * P6d-2：从 :app HomePageUtils 拆分（包名不变）。
 * 纯函数部分留 commonMain；[saveImageToGallery] 为 expect（android = MediaStore 原逻辑，
 * desktop = pictures 目录写文件，ios = no-op false）。
 */

/**
 * 将首页分类转换为高级搜索请求参数。
 *
 * 仅写入分类中存在的参数，避免向搜索页传递空值。
 *
 * @receiver 首页分类数据
 * @return 可直接用于高级搜索的参数映射
 */
fun HomeCategory.toAdvancedSearchParams(): Map<String, String> = buildMap {
    genre?.let { put("genre", it) }
    sort?.let { put("sort", it) }
    tags?.let { put("tags", it) }
}

/**
 * 下载远程图片并保存到系统相册/图片目录。
 *
 * P6d-2：原 internal 去除（:app AnnouncementDialog 跨模块调用）。
 *
 * @param imageUrl 需要保存的图片地址
 * @return 是否保存成功（调用方据此驱动 toast）
 */
expect suspend fun saveImageToGallery(imageUrl: String): Boolean
