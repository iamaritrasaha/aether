package com.foresightlabs.aether.ui.conversation
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foresightlabs.aether.domain.model.PollChoice
import com.foresightlabs.aether.domain.model.PollKind
import com.foresightlabs.aether.domain.model.PollPresentation
import com.foresightlabs.aether.ui.theme.AetherEmber
import com.foresightlabs.aether.ui.theme.LocalAetherColors
import com.foresightlabs.aether.ui.theme.LocalReducedMotion
import com.foresightlabs.aether.ui.theme.ManropeFontFamily

/**
 * A Telegram poll.
 *
 * Every number shown is Telegram's: vote counts, the total, and the percentages
 * (which are the server's own rounded values, so they agree with what every other
 * client shows). Nothing is computed locally, and nothing is shown optimistically —
 * a vote in flight is drawn as in flight until the server confirms it.
 */
@Composable
fun PollBubble(
    poll: PollPresentation,
    contentColor: Color,
    metaColor: Color,
    onVote: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAetherColors.current
    // Multiple-answer polls collect a set before submitting; single-answer polls
    // submit on tap, which is what Telegram itself does.
    var staged by remember(poll.id, poll.hasVoted) { mutableStateOf(emptySet<Int>()) }

    Column(modifier = modifier.testTag("poll_${poll.id}")) {
        Text(
            text = poll.question,
            fontFamily = ManropeFontFamily,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = poll.subtitle,
            fontFamily = ManropeFontFamily,
            fontSize = 11.5.sp,
            color = metaColor,
            modifier = Modifier.testTag("poll_subtitle")
        )
        Spacer(modifier = Modifier.height(8.dp))

        poll.choices.forEach { choice ->
            PollChoiceRow(
                choice = choice,
                poll = poll,
                isStaged = choice.index in staged,
                contentColor = contentColor,
                metaColor = metaColor,
                accent = colors.accent,
                onClick = {
                    if (!poll.canVote) return@PollChoiceRow
                    if (poll.allowsMultipleAnswers) {
                        staged = if (choice.index in staged) {
                            staged - choice.index
                        } else {
                            staged + choice.index
                        }
                    } else {
                        onVote(listOf(choice.index))
                    }
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (poll.allowsMultipleAnswers && poll.canVote) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AetherEmber.Shapes.Pill)
                    .background(
                        if (staged.isEmpty()) {
                            colors.accent.copy(alpha = 0.15f)
                        } else {
                            colors.accent.copy(alpha = 0.30f)
                        }
                    )
                    .clickable(enabled = staged.isNotEmpty()) { onVote(staged.sorted()) }
                    .padding(vertical = 10.dp)
                    .testTag("poll_submit"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (staged.isEmpty()) "Select an option" else "Vote",
                    fontFamily = ManropeFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }

        // Only ever shown once Telegram has actually sent it, which is after the
        // quiz has been answered.
        poll.explanation?.takeIf { poll.showResults }?.let { explanation ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = explanation,
                fontFamily = ManropeFontFamily,
                fontSize = 12.5.sp,
                color = metaColor,
                modifier = Modifier.testTag("poll_explanation")
            )
        }
    }
}

@Composable
private fun PollChoiceRow(
    choice: PollChoice,
    poll: PollPresentation,
    isStaged: Boolean,
    contentColor: Color,
    metaColor: Color,
    accent: Color,
    onClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val target = if (poll.showResults) choice.votePercentage / 100f else 0f
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) spring(stiffness = 10_000f) else spring(),
        label = "poll_fill"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = poll.canVote) { onClick() }
            .testTag("poll_option_${choice.index}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceMarker(
                choice = choice,
                poll = poll,
                isStaged = isStaged,
                accent = accent,
                metaColor = metaColor
            )
            Text(
                text = choice.text,
                fontFamily = ManropeFontFamily,
                fontSize = 14.sp,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (poll.showResults) {
                Text(
                    text = "${choice.votePercentage}%",
                    fontFamily = ManropeFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = metaColor,
                    modifier = Modifier.testTag("poll_percent_${choice.index}")
                )
            }
        }

        if (poll.showResults) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(AetherEmber.Shapes.Pill)
                    .background(metaColor.copy(alpha = 0.20f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .clip(AetherEmber.Shapes.Pill)
                        .background(if (choice.isChosen) accent else metaColor.copy(alpha = 0.55f))
                )
            }
        }
    }
}

@Composable
private fun ChoiceMarker(
    choice: PollChoice,
    poll: PollPresentation,
    isStaged: Boolean,
    accent: Color,
    metaColor: Color
) {
    val isQuizResult = poll.kind == PollKind.QUIZ && poll.showResults
    val border = when {
        choice.isChosen || isStaged -> accent
        isQuizResult && choice.isCorrect -> accent
        else -> metaColor.copy(alpha = 0.45f)
    }

    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(AetherEmber.Shapes.Pill)
            .border(1.5.dp, border, AetherEmber.Shapes.Pill)
            .background(
                if (choice.isChosen || isStaged) accent.copy(alpha = 0.25f) else Color.Transparent
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            choice.isBeingChosen -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(AetherEmber.Shapes.Pill)
                    .background(metaColor)
            )
            isQuizResult && choice.isChosen && !choice.isCorrect -> Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Incorrect",
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(12.dp)
            )
            (choice.isChosen || isStaged) || (isQuizResult && choice.isCorrect) -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
