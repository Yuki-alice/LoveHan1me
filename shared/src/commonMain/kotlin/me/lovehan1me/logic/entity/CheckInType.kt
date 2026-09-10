package me.lovehan1me.logic.entity

import me.lovehan1me.Res
import me.lovehan1me.checkin_type_masturbation
import me.lovehan1me.checkin_type_oral
import me.lovehan1me.checkin_type_other
import me.lovehan1me.checkin_type_sex
import me.lovehan1me.checkin_type_wet_dream
import org.jetbrains.compose.resources.StringResource

// P6d-2：从 :app 下沉（包名不变）。displayNameRes: Int → StringResource（P6c ReportReason 模式）。
enum class CheckInType(val displayNameRes: StringResource, val storeName: String) {
    MASTURBATION(Res.string.checkin_type_masturbation, "自慰"),
    WET_DREAM(Res.string.checkin_type_wet_dream, "梦遗"),
    SEX(Res.string.checkin_type_sex, "做爱"),
    ORAL(Res.string.checkin_type_oral, "口交"),
    OTHER(Res.string.checkin_type_other, "其它");

    companion object {
        fun fromDisplayName(name: String): CheckInType =
            entries.firstOrNull { it.storeName == name } ?: MASTURBATION
    }
}
