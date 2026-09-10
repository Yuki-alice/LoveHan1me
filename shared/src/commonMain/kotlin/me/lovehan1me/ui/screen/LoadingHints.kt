package me.lovehan1me.ui.screen

import me.lovehan1me.logic.model.AppLanguage

/**
 * P6d-4F：加载等待趣味文案（原 :app res string-array loading_hints）。
 * CMP 资源生成器对 string-array/新增长文本键静默截断（见 P6d-4F 记录），
 * 故按 AppLanguage 在 Kotlin 侧直供，随 SettingsRepository.funLoadingHints 开关使用。
 */
    private val EN = listOf(
        "Hmph! I-it's not like I kept you waiting! The router just sneezed…",
        "Idiot! Staring at it won't make it go faster! …Okay, just give me three more seconds!",
        "(Hurriedly hides the chip bag) O-oh, you caught me! Lemme just swallow this bite and I'm on it!",
        "Whoa! The data cables seem to have eloped and tangled together! Untangling them now!",
        "Packing happiness into a ZIP file for you… Estimated extraction time: Right now!",
        "The progress bar is sneaking bites of tiramisu—current status: bottom cookie crumbs are gone!",
        "Progress bar of dark powers, reveal thy true speed before me— (strikethrough) …Actually, let's stick with this speed!",
        "Hunting for interdimensional signals… Beep beep— Signal strength at max, but a cat bit the wire. Hold on!",
        "I know you're in a hurry, but chill~! Blink your eyes and turn your neck left three times… Yep, just like that!",
        "Activating spell card: Pot of Greed! …Oops, I drew even more loading time. Sorry~!",
        "Your loading request has been priority-queue-jumped by the hero squad! Current rank: #0 (VIP lane)!",
        "Rubbing my hands in excitement till they're smoking…",
        "◝(⑅•ᴗ•⑅)◜..°♡ Transmitting a bundle of cuteness your way—please sign for it~",
    )

    private val ZH_CN = listOf(
        "哼！才、才没有让你久等呢！是路由器刚才打了个喷嚏啦……",
        "笨蛋！盯着看它也不会变快啦！……好啦，再给我三秒就好！",
        "（慌慌张张藏起薯片袋）被…被发现了呢！等我咽下这口就来！",
        "呜哇！前面的数据线好像偷偷私奔缠在一起了！正在拆散它们！",
        "正在把快乐打包成压缩包发送给你……预计解压时间：马上！",
        "进度条正在偷吃提拉米苏，目前进度：底层饼干碎已吃完！",
        "隐藏着黑暗力量的进度条啊，在我面前用你真正的速度（划掉）……还是用现在的速度吧！",
        "正在捕捉异世界电波……哔哔——信号强度满格，但猫猫把线咬断了，稍等！",
        "知道你急，但先别急嘛~ 趁现在眨眨眼，把脖子向左扭三圈……对，就是这样！",
        "发动魔法卡：强欲之壶！……糟糕，抽到了更多的加载时间，对不起嘛！",
        "您的加载请求已被勇者小队插队处理，当前排名：第0位（贵宾通道）！",
        "搓手手期待到手指都冒烟啦……",
        "◝(⑅•ᴗ•⑅)◜..°♡ 正在把这份可爱传送给你，请签收~",
    )

    private val ZH_TW = listOf(
        "哼！才、才沒有讓你等很久啦！是路由器剛打了個噴嚏啦……",
        "笨蛋！一直盯著它看也不會變快啦！……好啦，再給我三秒就好！",
        "（慌慌張張藏起洋芋片袋）被…被發現了呢！等我吞下這口就來！",
        "嗚哇！前面的數據線好像偷偷私奔纏在一起了！正在拆散它們！",
        "正在把快樂打包成壓縮檔傳送給你……預估解壓時間：馬上！",
        "進度條正在偷吃提拉米蘇，目前進度：底層餅乾碎已吃完！",
        "隱藏著黑暗力量的進度條啊，在我面前用你真正的速度（劃掉）……還是用現在的速度吧！",
        "正在捕捉異世界電波……嗶嗶——訊號強度滿格，但貓貓把線咬斷了，稍等！",
        "知道你急，但先別急嘛~ 趁現在眨眨眼，把脖子向左扭三圈……對，就是這樣！",
        "發動魔法卡：強欲之壺！……糟糕，抽到了更多的載入時間，對不起嘛！",
        "您的載入請求已被勇者小隊插隊處理，目前排名：第0位（VIP通道）！",
        "搓手手期待到手指都冒煙啦……",
        "◝(⑅•ᴗ•⑅)◜..°♡ 正在把這份可愛傳送給你，請簽收~",
    )

fun loadingHints(language: AppLanguage): List<String> = when (language) {
    AppLanguage.ENGLISH -> EN
    AppLanguage.CHINESE_SIMPLIFIED -> ZH_CN
    AppLanguage.CHINESE_TRADITIONAL -> ZH_TW
    AppLanguage.SYSTEM -> EN
}
