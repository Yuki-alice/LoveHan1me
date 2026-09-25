//
//  PlayerUITests.swift
//  Gate4-2：播放页冒烟（mediamp-avkit 新栈 + P3 控件改动的端到端验证）。
//  流程：首启弹窗链 → 首页点卡 → 详情/播放页 25s → 前台存活 + 时间行渲染 + 截图。
//  时间文本（MM:SS）不断言具体值，只断言"进度/时长行组合出来且引擎报了时长"，
//  即 UI→Controller→Engine 状态链全通。若流被 CF 挡住导致时长恒 0，此条会红 ——
//  那是有效信号（说明该环境播不了），不是误报，去看截图与日志定因。

import XCTest

final class PlayerUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

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
            if cont.isHittable {
                cont.tap()
                sleep(1)
            }
        }
    }

    private func openFirstVideo(_ app: XCUIApplication) {
        app.launch()
        dismissStartupDialogs(app)

        let homeTitle = app.staticTexts["最新里番"]
        let homeTitleEn = app.staticTexts["Latest"]
        var ready = homeTitle.waitForExistence(timeout: 60)
        if !ready { ready = homeTitleEn.waitForExistence(timeout: 5) }
        XCTAssertTrue(ready, "首页未就绪")
        sleep(3)

        let card = app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.29))
        card.tap()
    }

    private func attachShot(_ app: XCUIApplication, name: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.lifetime = .keepAlways
        shot.name = name
        add(shot)
    }

    func testPlayerPageStaysForeground() throws {
        let app = XCUIApplication()
        openFirstVideo(app)
        sleep(25)
        XCTAssertEqual(app.state, .runningForeground, "播放页 25s 内闪退（mediamp-avkit 新栈重点观察）")
        attachShot(app, name: "player-25s")
    }

    func testPlayerTimeRenders() throws {
        let app = XCUIApplication()
        openFirstVideo(app)

        // 时间行（当前/总时长如 00:55/04:47）：出现即 UI 组合成功且引擎报了时长。
        let timeTexts = app.staticTexts.matching(
            NSPredicate(format: "label MATCHES %@", "\\d{1,3}:\\d{2}")
        )
        let found = timeTexts.firstMatch.waitForExistence(timeout: 40)
        attachShot(app, name: "player-time")
        XCTAssertTrue(found, "40s 内没见到时间文本：播放 UI 未组合或引擎没报时长（先看截图定是 UI 还是流）")
    }
}
