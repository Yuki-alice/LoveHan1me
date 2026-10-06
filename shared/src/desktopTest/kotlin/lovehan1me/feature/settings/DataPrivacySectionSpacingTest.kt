package lovehan1me.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.feature.settings.model.HomeSettingsActions
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.preview.HanimePreviewTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 数据和隐私页组间距回归（用户报障过组头贴卡，实测 16dp 健康，留作守卫）。
 *
 * 各组静态结构一致（spacedBy 4dp + 组顶 8dp + 标题纵向 8dp），
 * 本用例 headless 量出"上一卡底边 → 组标题顶边"的真实距离，塌陷即红。
 */
@OptIn(ExperimentalTestApi::class)
class DataPrivacySectionSpacingTest {

    @Test
    fun `缓存组头与上一卡不重叠不断裂`() {
        runComposeUiTest {
            setContent {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            dataPrivacySection(
                                previewHomeSettingsState(),
                                noopActions(),
                            )
                        }
                    }
                }
            }

            onNodeWithText("缓存").performScrollTo()
            val cardBottom = onNodeWithText("导入整机数据").getBoundsInRoot().bottom
            val titleTop = onNodeWithText("缓存").getBoundsInRoot().top
            // getBoundsInRoot() 给的是 DpRect：静态预期 20dp，底线取 12dp。
            val gap = titleTop - cardBottom
            println("SECTION_GAP: cardBottom=$cardBottom titleTop=$titleTop gap=$gap")
            assertTrue(
                gap >= 12.dp,
                "缓存组头与上一卡间距 $gap，小于底线 12.dp（静态预期约 20.dp）",
            )
        }
    }

    private fun noopActions() = HomeSettingsActions(
        videoLanguageChange = {},
        videoQualityChange = {},
        darkModeChange = {},
        themeIdChange = {},
        amoledChange = {},
        dynamicSubjectThemeChange = {},
        hapticFeedbackChange = {},
        funLoadingHintsChange = {},
        contrastLevelChange = {},
        allowPipModeChange = {},
        allowResumePlaybackChange = {},
        autoPlayOnEnterChange = {},
        showPlayedIndicatorChange = {},
        searchArtistIgnoreVideoTypeChange = {},
        disableMobileDataWarningChange = {},
        navBarStyleChange = {},
        checkInEnabledChange = {},
        disableCommentsChange = {},
        collapseDownloadedGroupChange = {},
        searchGridColumnsConfigChange = {},
        secureModeChange = {},
        alwaysShowUpdateCardChange = {},
        displayDensityChange = {},
        triggerCrash = {},
        homeCategoryPreferencesChange = { _, _ -> },
        openAppLanguageSettings = {},
        openApplyDeepLinks = {},
        openOpenSourceLicense = {},
        clearCache = {},
        exportBackup = {},
        importBackup = {},
        submitBug = {},
        openForum = {},
    )
}
