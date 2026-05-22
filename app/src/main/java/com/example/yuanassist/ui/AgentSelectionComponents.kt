package com.example.yuanassist.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yuanassist.model.AgentRepository
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.QuietInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageToggleRow

private const val PREFS_AGENT_FILTER = "agent_filter_prefs"
private const val KEY_SHOW_DAIHAOYUAN = "show_daihaoyuan_agents"
private val DAIHAOYUAN_EXTRA_AGENTS = listOf(
    "吕布", "刘璋", "夏侯渊", "酆公珠", "酆公玖", "法正", "庞德",
    "SP陈登", "SP史子渺", "曹丕", "程普", "钟繇", "蒯良", "陈群",
    "卢植", "简雍", "郭女王", "周忠", "陈纪", "陈应",
)
private val DAIHAOYUAN_HIDDEN_ALIASES = DAIHAOYUAN_EXTRA_AGENTS.toSet() + setOf(
    "庞曦",
    "SP史子眇",
)

@Composable
fun SharedAgentPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_AGENT_FILTER, Context.MODE_PRIVATE) }
    var includeDaihaoYuan by remember { mutableStateOf(prefs.getBoolean(KEY_SHOW_DAIHAOYUAN, false)) }
    val agents = remember(includeDaihaoYuan) {
        buildSelectableAgentList(includeDaihaoYuan)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = TitleInk, fontFamily = FontFamily.Serif)
            }
        },
        title = { Text("选择出战密探", color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SubpageToggleRow(
                    title = "代号鸢",
                    subtitle = "显示扩展密探",
                    checked = includeDaihaoYuan,
                    onCheckedChange = {
                        includeDaihaoYuan = it
                        prefs.edit().putBoolean(KEY_SHOW_DAIHAOYUAN, it).apply()
                    },
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(agents) { agent ->
                        AgentPickerItem(agentName = agent, onClick = { onSelect(agent) })
                    }
                }
            }
        },
    )
}

@Composable
fun SharedTalentPickerDialog(
    agentName: String,
    selectedTalentId: Int?,
    usedTalentIds: Set<Int>,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val talents = remember(agentName) {
        AgentRepository.AGENT_MAP[agentName]?.talents?.toList().orEmpty().sortedBy { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GlassPanel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onSelect(null) }) {
                    Text("清空", color = HighlightGold, fontFamily = FontFamily.Serif)
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = TitleInk, fontFamily = FontFamily.Serif)
                }
            }
        },
        title = {
            Text(
                text = "$agentName 命盘",
                color = TitleInk,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(talents) { (id, rawName) ->
                    val disabled = id != selectedTalentId && usedTalentIds.contains(id)
                    TalentChoiceChip(
                        text = rawName,
                        selected = selectedTalentId == id,
                        enabled = !disabled,
                        onClick = { onSelect(id) },
                    )
                }
            }
        },
    )
}

@Composable
fun AgentAvatar(agentName: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(agentName) {
        if (agentName.isNullOrBlank()) null else runCatching {
            context.assets.open("$agentName.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.42f))
            .border(1.dp, PaperLine.copy(alpha = 0.62f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = agentName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = agentName?.take(1) ?: "?",
                color = QuietInk,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
            )
        }
    }
}

@Composable
fun TalentValueChip(
    text: String,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val displayText = text.removePrefix("橙").removePrefix("紫")
    val background = when {
        placeholder -> Color.White.copy(alpha = 0.25f)
        text.startsWith("橙") -> Color(0xFFFFE2B6)
        text.startsWith("紫") -> Color(0xFFE7DBFF)
        else -> Color(0xFFDCEBFF)
    }
    val border = when {
        placeholder -> GlassStroke.copy(alpha = 0.25f)
        text.startsWith("橙") -> Color(0xFFD89A44)
        text.startsWith("紫") -> Color(0xFF8C74D9)
        else -> Color(0xFF4F8ED8)
    }
    val clickModifier = if (onClick == null) {
        modifier
    } else {
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }
    Box(
        modifier = clickModifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayText,
            color = if (placeholder) QuietInk else TitleInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun buildTalentPreview(agentName: String, talents: List<Int?>): String {
    val names = talents.mapNotNull { id ->
        id ?: return@mapNotNull null
        val raw = resolveTalentLabel(agentName, id).orEmpty()
        if (raw.isBlank()) "命盘$id" else raw.removePrefix("橙").removePrefix("紫")
    }
    return names.joinToString(" · ")
}

fun resolveTalentLabel(agentName: String?, talentId: Int?): String? {
    if (agentName.isNullOrBlank() || talentId == null) return null
    return AgentRepository.AGENT_MAP[agentName]?.talents?.get(talentId)
}

fun resolveTalentIdByLabel(agentName: String?, label: String): Int? {
    if (agentName.isNullOrBlank() || label.isBlank()) return null
    return AgentRepository.AGENT_MAP[agentName]?.talents
        ?.entries
        ?.firstOrNull { it.value == label }
        ?.key
}

fun buildSelectableAgentList(includeDaihaoYuan: Boolean): List<String> {
    val result = LinkedHashSet<String>()
    if (includeDaihaoYuan) {
        result.addAll(DAIHAOYUAN_EXTRA_AGENTS)
    }
    result.addAll(
        AgentRepository.ALL_AGENTS.filterNot { it in DAIHAOYUAN_HIDDEN_ALIASES },
    )
    return result.toList()
}

@Composable
private fun AgentPickerItem(agentName: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AgentAvatar(agentName, Modifier.size(46.dp))
        Text(
            text = agentName,
            color = TitleInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TalentChoiceChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        selected -> HighlightGold.copy(alpha = 0.82f)
        text.startsWith("橙") -> Color(0xFFD89A44)
        text.startsWith("紫") -> Color(0xFF8C74D9)
        else -> Color(0xFF4F8ED8)
    }
    val background = when {
        !enabled -> Color.White.copy(alpha = 0.18f)
        selected -> Color(0xFFFFF7E2)
        text.startsWith("橙") -> Color(0xFFFFE8C8)
        text.startsWith("紫") -> Color(0xFFEEE6FF)
        else -> Color(0xFFE6F1FF)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, border.copy(alpha = if (enabled) 0.82f else 0.32f), RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.removePrefix("橙").removePrefix("紫"),
            color = if (enabled) BodyInk else QuietInk,
            fontSize = 12.sp,
            fontFamily = FontFamily.Serif,
        )
    }
}
