package com.dsh.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dsh.mobile.ui.theme.LocalDsh
import kotlinx.coroutines.launch

/** 长文速览的载荷：从回答里切出的各小节（见 [splitSections]）。 */
data class LongReadDoc(val sections: List<ReadSection>)

/**
 * 评审稿《长答案先给地图》：顶部是章节 chips（点一下跳到该节），下面按节渲染全文。
 * 不做任何新解析——正文仍交给 [MarkdownText] 原样渲染；速览的入口在 ChatScreen 里。
 */
@Composable
fun LongReadScreen(doc: LongReadDoc, onDismiss: () -> Unit) {
    val palette = LocalDsh.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // 每节的 y 偏移（在滚动内容坐标系里），chip 点击时跳过去
    val offsets = remember { mutableStateMapOf<Int, Int>() }
    BackHandler { onDismiss() }
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回") { onDismiss() }
            Spacer(Modifier.width(10.dp))
            Text(
                "长文速览",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))

        val titled = doc.sections.withIndex().filter { it.value.title.isNotBlank() }
        if (titled.size >= 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                titled.forEach { (index, section) ->
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.surfaceHi)
                            .clickable {
                                offsets[index]?.let { y ->
                                    scope.launch { scroll.animateScrollTo(y) }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            doc.sections.forEachIndexed { index, section ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            offsets[index] = it.positionInParent().y.toInt()
                        },
                ) {
                    if (section.title.isNotBlank()) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                        )
                    }
                    if (section.body.isNotBlank()) {
                        MarkdownText(
                            section.body,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
