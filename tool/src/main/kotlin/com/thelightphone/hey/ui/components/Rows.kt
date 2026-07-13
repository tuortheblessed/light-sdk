package com.thelightphone.hey.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.hey.ui.HeyHabitIcons
import com.thelightphone.sdk.ui.LightSurfaceScheme
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@Composable
fun SectionHeader(
    label: String,
    count: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.5f.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = label, variant = LightTextVariant.Copy)
        if (!count.isNullOrBlank()) {
            LightText(
                text = count,
                variant = LightTextVariant.Copy,
                lighten = true,
            )
        }
    }
}

@Composable
fun HabitRow(
    title: String,
    done: Boolean,
    iconSlug: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LightThemeTokens.surfaceScheme == LightSurfaceScheme.Dark
    val iconRes = HeyHabitIcons.drawableRes(iconSlug, darkTheme = dark)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.35f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                // Filled = done; dimmed = not done (no separate ○/● mark).
                alpha = if (done) 1f else 0.32f,
                modifier = Modifier
                    .padding(end = 0.75f.gridUnitsAsDp())
                    .size(2f.gridUnitsAsDp()),
            )
        } else {
            LightText(
                text = if (done) "●" else "○",
                variant = LightTextVariant.Copy,
                lighten = !done,
                modifier = Modifier.padding(end = 0.75f.gridUnitsAsDp()),
            )
        }
        LightText(
            text = title,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lighten = !done,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun TodoRow(
    title: String,
    completed: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LightText(
        text = "${if (completed) "●" else "○"}  $title",
        variant = LightTextVariant.Copy,
        lighten = completed,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.35f.gridUnitsAsDp()),
    )
}

@Composable
fun StickyRow(
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LightText(
        text = "▌ ${content.singleLinePreview()}",
        variant = LightTextVariant.Copy,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.35f.gridUnitsAsDp()),
    )
}

@Composable
fun MoreRow(
    remaining: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LightText(
        text = "+$remaining MORE",
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.25f.gridUnitsAsDp()),
    )
}

@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = modifier.padding(vertical = 0.25f.gridUnitsAsDp()),
    )
}

internal fun String.singleLinePreview(max: Int = 40): String {
    val oneLine = replace('\n', ' ').trim()
    return if (oneLine.length <= max) oneLine else oneLine.take(max - 1) + "…"
}
