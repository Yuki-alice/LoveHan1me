package lovehan1me.ui.screen.home.dailycheckin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.week_streak_title
import lovehan1me.streak_title
import lovehan1me.sex_times
import lovehan1me.on_fire_title
import lovehan1me.nice_title
import lovehan1me.legend_title
import lovehan1me.keep_going_title
import lovehan1me.great_title
import lovehan1me.egg_singles
import lovehan1me.egg_nice
import lovehan1me.egg_god
import lovehan1me.champion_title
import lovehan1me.achievement_desc_top
import lovehan1me.achievement_desc_streak
import lovehan1me.achievement_desc_keep
import lovehan1me.achievement_desc_days
import lovehan1me.ach_scholar
import lovehan1me.ach_night_owl
import lovehan1me.ach_multi_type
import lovehan1me.ach_morning
import lovehan1me.ui.viewmodel.MonthlyStats
import kotlinx.datetime.LocalDate

/**
 * 成就展示区域。
 *
 * 根据 [checkedDays]、[monthlyTotal]、[bestStreak] 和 [stats] 展示不同等级的成就。
 *
 * @param checkedDays 已打卡天数
 * @param monthlyTotal 月累计打卡次数
 * @param bestStreak 最佳连续天数
 * @param stats 月度统计数据
 * @param modifier 修饰符
 */
@Composable
fun AchievementSection(
    modifier: Modifier = Modifier,
    checkedDays: Int,
    monthlyTotal: Int,
    bestStreak: Int,
    stats: MonthlyStats,
    todayCount: Int = 0,
    yearMonth: YearMonth = YearMonth.now(),
) {
    val today = today()
    val isSinglesDay =
        today.monthNumber == 11 && today.dayOfMonth == 11 && yearMonth.month == 11 && todayCount == 0

    AnimatedVisibility(
        visible = checkedDays > 0 || isSinglesDay,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isSinglesDay) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(
                        text = stringResource(Res.string.egg_singles),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val formattedTitles = mapOf(
                        "legend" to stringResource(Res.string.legend_title),
                        "champion" to stringResource(Res.string.champion_title),
                        "nice" to stringResource(Res.string.nice_title),
                        "onFire" to stringResource(Res.string.on_fire_title),
                        "great" to stringResource(Res.string.great_title),
                        "weekStreak" to stringResource(Res.string.week_streak_title),
                        "streak" to stringResource(Res.string.streak_title),
                    )

                    val formattedSubs = mapOf(
                        "god" to stringResource(Res.string.egg_god, monthlyTotal),
                        "top" to stringResource(Res.string.achievement_desc_top, monthlyTotal),
                        "nice" to stringResource(Res.string.egg_nice, monthlyTotal),
                        "days" to stringResource(Res.string.achievement_desc_days, checkedDays),
                        "streak" to stringResource(Res.string.achievement_desc_streak, bestStreak),
                    )

                    val mainRules = buildMainAchievementRules(
                        checkedDays = checkedDays,
                        monthlyTotal = monthlyTotal,
                        bestStreak = bestStreak,
                        formattedTitles = formattedTitles,
                        formattedSubs = formattedSubs,
                    )

                    val mainAchievements = evaluateMainAchievements(
                        rules = mainRules,
                        checkedDays = checkedDays,
                        monthlyTotal = monthlyTotal,
                        bestStreak = bestStreak,
                        defaultTitle = stringResource(Res.string.keep_going_title),
                        defaultSubtitle = stringResource(Res.string.achievement_desc_keep),
                    )

                    mainAchievements.forEach { achievement ->
                        if (achievement != mainAchievements.first()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(text = achievement.emoji, fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = achievement.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (stats.typeCounts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stats.typeCounts.entries
                        .sortedByDescending { it.value }
                        .take(STATS_TYPE_DISPLAY_COUNT)
                        .forEach { (type, count) ->
                            AchievementMiniCard(
                                emoji = typeEmoji(type),
                                value = count.toString(),
                                label = type,
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
            }

            val formattedLabels = mapOf(
                "multiType" to stringResource(Res.string.ach_multi_type),
                "nightOwl" to stringResource(Res.string.ach_night_owl),
                "morning" to stringResource(Res.string.ach_morning),
                "scholar" to stringResource(Res.string.ach_scholar),
                "sixTimes" to stringResource(Res.string.sex_times)
            )
            val extraRules = buildExtraAchievementRules(
                stats = stats,
                formattedLabels = formattedLabels,
            )
            val extraAchievements = evaluateExtraAchievements(
                rules = extraRules,
                stats = stats,
                maxCount = EXTRA_ACHIEVEMENT_DISPLAY_COUNT,
            )
            if (extraAchievements.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    extraAchievements.forEach { ach ->
                        AchievementMiniCard(
                            emoji = ach.emoji,
                            value = ach.title,
                            label = ach.subtitle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 小型成就卡片，用于 [AchievementSection] 内的横向列表。
 *
 * @param emoji 成就图标 emoji
 * @param value 成就数值
 * @param label 成就描述
 * @param modifier 修饰符
 */
@Composable
fun AchievementMiniCard(
    modifier: Modifier = Modifier,
    emoji: String,
    value: String,
    label: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 22.sp)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

