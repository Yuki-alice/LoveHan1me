package lovehan1me.core.domain.exception

/**
 * 检测到爬虫被封鎖
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2023/08/07 007 12:45
 */
open class CloudflareBlockedException(reason: String) : RuntimeException(reason)
