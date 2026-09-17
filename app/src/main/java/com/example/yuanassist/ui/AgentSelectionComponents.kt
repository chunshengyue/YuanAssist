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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.yuanassist.network.CloudGameAgent
import com.example.yuanassist.network.SupabaseRepository
import com.example.yuanassist.utils.CloudGameAgentCache
import com.example.yuanassist.ui.main.theme.BodyInk
import com.example.yuanassist.ui.main.theme.GlassPanel
import com.example.yuanassist.ui.main.theme.GlassStroke
import com.example.yuanassist.ui.main.theme.HighlightGold
import com.example.yuanassist.ui.main.theme.PaperLine
import com.example.yuanassist.ui.main.theme.QuietInk
import com.example.yuanassist.ui.main.theme.TitleInk
import com.example.yuanassist.ui.subpage.SubpageToggleRow
import com.example.yuanassist.ui.subpage.SubpageTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val PREFS_AGENT_FILTER = "agent_filter_prefs"
private const val KEY_SHOW_DAIHAOYUAN = "show_daihaoyuan_agents"
private val NON_DAIHAOYUAN_PREFIX_AGENTS = listOf("郭女王", "庞德", "吕布", "曹丕")
private val DAIHAOYUAN_EXTRA_AGENTS = listOf(
    "周泰", "陈琳",
    "赵云", "司马孚", "张松", "孙辅",
    "孟获", "孙静",
    "酆公珠", "酆公玖", "法正",
    "SP陈登", "SP史子渺", "蒯良", "陈群",
    "卢植", "简雍", "周忠", "陈纪", "陈应",
)
private val DAIHAOYUAN_HIDDEN_ALIASES = DAIHAOYUAN_EXTRA_AGENTS.toSet() + setOf(
    "庞曦",
    "SP史子眇",
)

@Composable
fun SharedAgentPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    agentOptions: List<String>? = null,
    title: String = "选择出战密探",
    showCloudAgents: Boolean = false,
    gameVersion: Int = 1,
    agentFilter: ((String) -> Boolean)? = null,
    priorityAgents: List<String> = emptyList(),
    priorityLabel: String? = null,
    showSearch: Boolean = false,
    initialIncludeDaihaoYuan: Boolean? = null,
    syncCloudGameWithDaihaoToggle: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_AGENT_FILTER, Context.MODE_PRIVATE) }
    var includeDaihaoYuan by remember(initialIncludeDaihaoYuan) {
        mutableStateOf(initialIncludeDaihaoYuan ?: prefs.getBoolean(KEY_SHOW_DAIHAOYUAN, false))
    }
    var includeCloudAgents by remember { mutableStateOf(false) }
    var cloudAgents by remember { mutableStateOf<List<CloudGameAgent>>(emptyList()) }
    var cloudLoading by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val agents = remember(includeDaihaoYuan, agentOptions, agentFilter, priorityAgents) {
        val source = agentOptions?.distinct() ?: buildSelectableAgentList(includeDaihaoYuan)
        val filtered = agentFilter?.let { predicate -> source.filter(predicate) } ?: source
        priorityAgents.filter { it in filtered } + filtered.filterNot { it in priorityAgents }
    }
    val resolvedCloudGameVersion = if (syncCloudGameWithDaihaoToggle) {
        if (includeDaihaoYuan) 0 else 1
    } else {
        gameVersion
    }
    val filteredAgents = remember(agents, searchQuery) {
        agents.filter { agent -> agent.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val filteredCloudAgents = remember(cloudAgents, searchQuery) {
        cloudAgents.filter { agent -> agent.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    LaunchedEffect(includeCloudAgents, resolvedCloudGameVersion) {
        if (!includeCloudAgents) return@LaunchedEffect
        cloudLoading = true
        cloudError = ""
        SupabaseRepository.listCloudGameAgents(
            gameVersion = resolvedCloudGameVersion,
            onSuccess = { result ->
                cloudAgents = result.filter { it.name.isNotBlank() && it.name !in AgentRepository.ALL_AGENTS }
                cloudLoading = false
            },
            onError = { message ->
                cloudAgents = emptyList()
                cloudError = message
                cloudLoading = false
            },
        )
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
        title = { Text(title, color = TitleInk, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (agentOptions == null) {
                    SubpageToggleRow(
                        title = "代号鸢",
                        subtitle = "勾选为代号鸢，取消为如鸢",
                        checked = includeDaihaoYuan,
                        onCheckedChange = {
                            includeDaihaoYuan = it
                            prefs.edit().putBoolean(KEY_SHOW_DAIHAOYUAN, it).apply()
                        },
                    )
                }
                if (showCloudAgents) {
                    SubpageToggleRow(
                        title = "新出密探",
                        subtitle = "读取尚未实装到 App 的云端密探",
                        checked = includeCloudAgents,
                        onCheckedChange = { includeCloudAgents = it },
                    )
                }
                if (showSearch) {
                    SubpageTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "搜索密探",
                        labelFontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 48.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showCloudAgents && includeCloudAgents) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "新出密探",
                                color = HighlightGold,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        when {
                            cloudLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("正在读取云端密探...", color = QuietInk, fontFamily = FontFamily.Serif)
                            }
                            cloudError.isNotBlank() -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("云端密探读取失败：$cloudError", color = QuietInk, fontFamily = FontFamily.Serif)
                            }
                            filteredCloudAgents.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("暂无未实装到 App 的新密探", color = QuietInk, fontFamily = FontFamily.Serif)
                            }
                            else -> items(filteredCloudAgents, key = { it.id }) { agent ->
                                AgentPickerItem(
                                    agentName = agent.name,
                                    avatarUrl = agent.avatarUrl,
                                    onClick = { onSelect(agent.name) },
                                )
                            }
                        }
                    }
                    if (filteredAgents.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("未找到匹配的密探", color = QuietInk, fontFamily = FontFamily.Serif)
                        }
                    }
                    items(filteredAgents) { agent ->
                        AgentPickerItem(
                            agentName = agent,
                            badgeText = priorityLabel?.takeIf { agent in priorityAgents },
                            onClick = { onSelect(agent) },
                        )
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
fun AgentAvatar(
    agentName: String?,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
) {
    val context = LocalContext.current
    var remoteBitmap by remember(avatarUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cloudAvatarVersion by remember(agentName) { mutableStateOf(0) }
    LaunchedEffect(avatarUrl) {
        remoteBitmap = avatarUrl?.takeIf { it.isNotBlank() }?.let { url ->
            withContext(Dispatchers.IO) {
                runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
            }
        }
    }
    LaunchedEffect(agentName, avatarUrl) {
        if (agentName.isNullOrBlank() || !avatarUrl.isNullOrBlank()) return@LaunchedEffect
        CloudGameAgentCache.ensureAvailable(context, listOf(agentName)) {
            cloudAvatarVersion++
        }
    }
    val localBitmap = remember(agentName, avatarUrl, cloudAvatarVersion) {
        if (agentName.isNullOrBlank() || !avatarUrl.isNullOrBlank()) null else runCatching {
            context.assets.open("$agentName.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: CloudGameAgentCache.avatarBitmap(context, agentName)
    }
    val bitmap = remoteBitmap ?: localBitmap
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
        result.addAll(NON_DAIHAOYUAN_PREFIX_AGENTS)
    } else {
        result.addAll(NON_DAIHAOYUAN_PREFIX_AGENTS)
    }
    result.addAll(
        AgentRepository.ALL_AGENTS.filterNot {
            it in DAIHAOYUAN_HIDDEN_ALIASES || it in NON_DAIHAOYUAN_PREFIX_AGENTS
        },
    )
    return result.toList()
}

@Composable
private fun AgentPickerItem(
    agentName: String,
    avatarUrl: String? = null,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            AgentAvatar(agentName, Modifier.size(46.dp), avatarUrl)
            Text(
                text = agentName,
                color = TitleInk,
                fontSize = 12.sp,
                fontFamily = FontFamily.Serif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (badgeText != null) {
            Text(
                text = badgeText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(HighlightGold, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
        }
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
