package com.jmstudiox.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmstudiox.app.data.model.Tool
import com.jmstudiox.app.data.model.EditorSessionStore
import com.jmstudiox.app.data.model.toolList
import com.jmstudiox.app.ui.screens.ProjectsScreen
import com.jmstudiox.app.ui.screens.VideoEditorScreen
import com.jmstudiox.app.utils.MediaUtils
import androidx.activity.result.PickVisualMediaRequest
private enum class Tab { HOME, TOOLS, PROJECTS, SETTINGS }

@Composable
fun JMStudioXApp(
    darkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit
) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var selectedTool by remember { mutableStateOf<Tool?>(null) }
    var editorUri by remember { mutableStateOf<Uri?>(null) }
    var editorName by remember { mutableStateOf("Video") }
    var pendingSession by remember { mutableStateOf<com.jmstudiox.app.data.model.EditorSession?>(null) }
    var showResumeDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val saved = EditorSessionStore.load(context)
        if (saved != null) {
            pendingSession = saved
            showResumeDialog = true
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            editorUri = uri
            editorName = MediaUtils.displayName(context, uri)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Tab.HOME to ("Home" to Icons.Default.Home),
                    Tab.TOOLS to ("Tools" to Icons.Default.Build),
                    Tab.PROJECTS to ("Projects" to Icons.Default.Folder),
                    Tab.SETTINGS to ("Settings" to Icons.Default.Settings)
                ).forEach { (t, pair) ->
                    NavigationBarItem(
                        selected = tab == t && selectedTool == null && editorUri == null,
                        onClick = {
                            tab = t
                            selectedTool = null
                            editorUri = null
                        },
                        icon = { Icon(pair.second, null) },
                        label = { Text(pair.first) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                editorUri != null -> VideoEditorScreen(
                    uri = editorUri!!,
                    fileName = editorName,
                    initialSession = pendingSession?.takeIf { it.sourceUri == editorUri.toString() },
                    onBack = { editorUri = null }
                )
                else -> AnimatedContent(
                    targetState = Pair(tab, selectedTool),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen"
                ) { (currentTab, tool) ->
                    if (tool != null) {
                        ToolDetail(
                            tool = tool,
                            onBack = { selectedTool = null },
                            onVideoPick = {
                                picker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
                            }
                        )
                    } else {
                        when (currentTab) {
                            Tab.HOME -> HomeScreen { selectedTool = it }
                            Tab.TOOLS -> ToolsScreen { selectedTool = it }
                            Tab.PROJECTS -> ProjectsScreen()
                            Tab.SETTINGS -> SettingsScreen(darkTheme, onThemeChanged)
                        }
                    }
                }
            }
        }
    }

    if (showResumeDialog && pendingSession != null) {
        val saved = pendingSession!!
        AlertDialog(
            onDismissRequest = { /* Require an explicit choice so the recovery session is not lost. */ },
            icon = { Icon(Icons.Default.Restore, contentDescription = null) },
            title = { Text("Continue your last edit?") },
            text = { Text("An unfinished project was saved: ${saved.fileName}. Continue where you left off, or start a new edit?") },
            confirmButton = {
                Button(onClick = {
                    editorUri = saved.source()
                    editorName = saved.fileName
                    showResumeDialog = false
                }) { Text("Continue Editing") }
            },
            dismissButton = {
                TextButton(onClick = {
                    EditorSessionStore.clear(context)
                    pendingSession = null
                    editorUri = null
                    showResumeDialog = false
                }) { Text("Start Over") }
            }
        )
    }
}

@Composable
private fun HomeScreen(onToolClick: (Tool) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("JM StudioX", fontSize = 32.sp)
            Text("Create. Edit. Transform.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Pro Video Editor", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("InShot-style timeline editing powered by FFmpeg.")
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { onToolClick(toolList.first()) }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start Editing")
                    }
                }
            }
        }
        item { Text("Quick Tools", style = MaterialTheme.typography.titleLarge) }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(520.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(toolList.take(10)) { tool ->
                    ToolCard(tool, onToolClick)
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen(onToolClick: (Tool) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("All Tools", style = MaterialTheme.typography.headlineSmall) }
        items(toolList.size) { index ->
            val tool = toolList[index]
            OutlinedButton(
                onClick = { onToolClick(tool) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(tool.icon, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tool.name)
                    Text(tool.description, style = MaterialTheme.typography.labelSmall)
                }
                Icon(Icons.Default.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun ToolCard(tool: Tool, onClick: (Tool) -> Unit) {
    Card(
        onClick = { onClick(tool) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(tool.icon, null, Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(tool.name, style = MaterialTheme.typography.titleMedium)
            Text(tool.description, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToolDetail(
    tool: Tool,
    onBack: () -> Unit,
    onVideoPick: () -> Unit
) {
    if (tool.name == "Video Editor" || tool.name == "Cut & Trim") {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text(tool.name, style = MaterialTheme.typography.headlineSmall)
            Text(tool.description)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onVideoPick) {
                Icon(Icons.Default.VideoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose Video")
            }
            Spacer(Modifier.height(12.dp))
            Text("After selecting a video, the full timeline editor opens.")
        }
    } else {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text(tool.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(tool.description)
            Spacer(Modifier.height(20.dp))
            Text(
                "This tool will use the same FFmpeg engine. The timeline editor is the core editing workspace.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Dark Mode")
                    Text("Default professional dark workspace", style = MaterialTheme.typography.labelSmall)
                }
                Switch(darkTheme, onThemeChanged)
            }
        }
    }
}
