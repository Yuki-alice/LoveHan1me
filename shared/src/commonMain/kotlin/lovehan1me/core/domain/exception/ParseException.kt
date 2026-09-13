package lovehan1me.core.domain.exception

/**
 * 解析錯誤
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2023/08/05 005 16:20
 */
class ParseException : RuntimeException {

    constructor(
        funcName: String,
        varName: String
    ) : super("[Parse::$funcName => $varName] parse error!")

    constructor(reason: String) : super(reason)
}