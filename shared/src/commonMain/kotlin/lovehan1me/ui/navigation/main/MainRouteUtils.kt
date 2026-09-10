package lovehan1me.ui.navigation.main

fun shiftMonthCodeForPreview(code: String, delta: Int): String {
    var year = code.substring(0, 4).toInt()
    var month = code.substring(4, 6).toInt() + delta
    while (month < 1) {
        month += 12
        year -= 1
    }
    while (month > 12) {
        month -= 12
        year += 1
    }
    return year.toString().padStart(4, '0') + month.toString().padStart(2, '0')
}
