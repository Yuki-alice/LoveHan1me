//
//  NoticeAndBackupUITests.swift
//  阶段一 ④⑦⑨ 的模拟器自动化回归：
//   1. 首启弹窗链（使用须知 → 倒计时同意 → 下载来源「继续」）→ 进入主界面
//   2. 抽屉 → 设置 → 备份与恢复（④ 的入口可达性）
//
//  ⚠️ 已实测的坑（改查询前先看）：
//  - 倒计时期间同意按钮的 label 就是「请阅读 N 秒」且 Disabled，
//    直接查「我已阅读并同意」永远匹配不上——必须等 label 自行变化。
//  - 同意后还有第二个弹窗（下载来源 + 「继续」），它叠在抽屉上层，
//    会把抽屉入口全部挡出 accessibility 树——导航前必须先点掉。
//  - Compose 的已删除节点可能短暂残留在树里，断言「XX 已消失」要用轮询等，别用瞬时判断。
//

import XCTest

final class NoticeAndBackupUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// 中英双语言匹配按钮（Compose 有时把文本暴露成 staticText 而非 button）
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

    /// 处理完首启弹窗链（使用须知倒计时同意 → 下载来源「继续」），保证到达主界面。
    private func dismissStartupDialogs(_ app: XCUIApplication) {
        // ① 使用须知：倒计时期间按钮 label 是「请阅读 N 秒」
        let agreeZh = app.buttons["我已阅读并同意"]
        let agreeEn = app.buttons["I have read and agree"]
        if !agreeZh.exists && !agreeEn.exists {
            let counting = app.buttons.matching(
                NSPredicate(format: "label BEGINSWITH %@ OR label BEGINSWITH %@",
                            "请阅读", "Please read")
            ).firstMatch
            if counting.waitForExistence(timeout: 5) {
                // 等倒计时走完、label 变成「我已阅读并同意」（18s + 余量）
                _ = agreeZh.waitForExistence(timeout: 30) || agreeEn.waitForExistence(timeout: 1)
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

        // ② 下载来源弹窗：「你是从哪里下载的app？」—— 有 论坛/Telegram/GitHub 三个
        //    选项按钮 + 「继续」。不选来源直接点继续可能无效，先选一个再继续。
        let cont = app.buttons["继续"]
        if cont.waitForExistence(timeout: 5) {
            let source = anyButton(app, labels: ["GitHub", "论坛", "Telegram"])
            if let s = source, s.isHittable {
                s.tap()
                sleep(1)
            }
            if cont.isHittable {
                cont.tap()
                sleep(1)
            } else {
                // 选项点击后「继续」可能才变为可点，再等一下
                let enabled = expectation(for: NSPredicate(format: "isEnabled == true"),
                                          evaluatedWith: cont)
                wait(for: [enabled], timeout: 5)
                cont.tap()
                sleep(1)
            }
        }
    }

    /// 断言应用存活且首启弹窗已全部退场（防 CADisable… 崩溃回归）
    private func assertMainScreen(_ app: XCUIApplication) {
        XCTAssertEqual(app.state, .runningForeground, "应用不在前台（疑似崩溃）")

        // 「请阅读」倒计时按钮最多残留 18s；用轮询等须知页退场
        let noticePred = NSPredicate { _, _ in
            !app.staticTexts["使用须知"].exists && !app.staticTexts["Usage Notice"].exists
        }
        let gone = expectation(for: noticePred, evaluatedWith: app)
        wait(for: [gone], timeout: 20)
    }

    /// 用例 1：首启弹窗链全部走完 → 应用存活进入主界面
    func testAgreeNoticeReachesHome() {
        let app = XCUIApplication()
        app.launch()

        dismissStartupDialogs(app)
        sleep(2)
        assertMainScreen(app)
    }

    /// 用例 2：抽屉 → 设置 → 备份与恢复 入口可达（④）
    func testNavigateToBackupSettings() {
        let app = XCUIApplication()
        app.launch()
        dismissStartupDialogs(app)
        sleep(2)
        assertMainScreen(app)

        // 打开抽屉：汉堡按钮已带 contentDescription（open_menu 字符串，中英双语）
        if let menu = anyButton(app, labels: ["打开菜单", "Open menu"]), menu.isHittable {
            menu.tap()
        } else {
            // 兜底：左缘慢速拖拽（首页横向 pager 会吃掉普通滑动，须从最左缘起手）
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.02, dy: 0.55))
                .press(forDuration: 0.2,
                       thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.55)),
                       withVelocity: .slow, thenHoldForDuration: 0.1)
        }
        sleep(2)

        // 抽屉里的「设置」（中英双查，找不到先滚动）
        var settings = anyButton(app, labels: ["设置", "Settings"])
        if settings == nil {
            app.swipeUp()
            sleep(1)
            settings = anyButton(app, labels: ["设置", "Settings"])
        }
        guard let s = settings, s.isHittable else {
            dumpTree(app, reason: "抽屉里没找到「设置」入口")
            XCTFail("未找到设置入口")
            return
        }
        s.tap()
        sleep(2)

        // 设置页里找「备份」相关入口（④）
        var backup = anyButton(app, labels: ["备份", "Backup"])
        if backup == nil {
            app.swipeUp()
            sleep(1)
            backup = anyButton(app, labels: ["备份", "Backup"])
        }
        guard let b = backup, b.isHittable else {
            dumpTree(app, reason: "设置页里没找到「备份」入口（可能需要再滚动或入口文案不同）")
            XCTFail("未找到备份入口")
            return
        }
        b.tap()
        sleep(2)
        XCTAssertEqual(app.state, .runningForeground, "进入备份页后应用不在前台")
    }

    /// 失败时打印整棵 accessibility 树，用于迭代查询条件
    private func dumpTree(_ app: XCUIApplication, reason: String) {
        print("====== UITEST DUMP (\(reason)) ======")
        print(app.debugDescription)
        print("====== UITEST DUMP END ======")
    }
}
