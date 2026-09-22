//
//  VideoCardCrashUITests.swift
//  复现：首页点击视频卡片进详情页时闪退（SIGABRT，未捕获 Kotlin 异常）。
//  流程：首启弹窗链（如有）→ 首页 → 点首屏大卡 → 等 15s → 断言仍在前台。
//  若崩溃，.xcresult 会带走完整控制台（含 Kotlin 异常正文）。

import XCTest

final class VideoCardCrashUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func anyButton(_ app: XCUIApplication, labels: [String]) -> XCUIElement? {
        for l in labels {
            let b = app.buttons[l]
            if b.exists { return b }
        }
        for l in labels {
            let t = app.staticTexts[l]
            if t.exists { return t }
        }
        return nil
    }

    /// 首启弹窗链（使用须知倒计时同意 → 下载来源「继续」），已走过则秒过。
    private func dismissStartupDialogs(_ app: XCUIApplication) {
        let agreeZh = app.buttons["我已阅读并同意"]
        let agreeEn = app.buttons["I have read and agree"]
        if !agreeZh.exists && !agreeEn.exists {
            let counting = app.buttons.matching(
                NSPredicate(format: "label BEGINSWITH %@ OR label BEGINSWITH %@",
                            "请阅读", "Please read")
            ).firstMatch
            if counting.waitForExistence(timeout: 5) {
                _ = agreeZh.waitForExistence(timeout: 40) || agreeEn.waitForExistence(timeout: 1)
            }
        }
        if agreeZh.exists || agreeEn.exists {
            let btn = agreeZh.exists ? agreeZh : agreeEn
            let enabled = expectation(for: NSPredicate(format: "isEnabled == true"),
                                      evaluatedWith: btn)
            wait(for: [enabled], timeout: 10)
            btn.tap()
            sleep(1)
        }

        let cont = app.buttons["继续"]
        if cont.waitForExistence(timeout: 5) {
            if let s = anyButton(app, labels: ["GitHub", "论坛", "Telegram"]), s.isHittable {
                s.tap()
                sleep(1)
            }
            if cont.isHittable {
                cont.tap()
                sleep(1)
            }
        }
    }

    func testTapFirstVideoCardDoesNotCrash() throws {
        let app = XCUIApplication()
        app.launch()
        dismissStartupDialogs(app)

        // 首页就绪：中文标题出现即认为列表页已组合（冷启动 + 网关探测，放宽）。
        // 不用搜索框判定：它不一定暴露为 textField。
        let homeTitle = app.staticTexts["最新里番"]
        let homeTitleEn = app.staticTexts["Latest"]
        var ready = homeTitle.waitForExistence(timeout: 60)
        if !ready { ready = homeTitleEn.waitForExistence(timeout: 5) }
        if !ready {
            let shot = XCTAttachment(screenshot: app.screenshot())
            shot.lifetime = .keepAlways
            add(shot)
            let tree = XCTAttachment(string: app.debugDescription)
            tree.lifetime = .keepAlways
            add(tree)
        }
        XCTAssertTrue(ready, "首页未就绪")
        sleep(3)

        // 首屏大横幅卡中心（归一化坐标；布局大改时按截图重测）。
        let card = app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.29))
        card.tap()

        // 详情页加载 + 崩溃窗口。
        sleep(15)
        XCTAssertEqual(app.state, .runningForeground, "点卡后应用不在前台（闪退）")
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.lifetime = .keepAlways
        add(shot)
    }
}
