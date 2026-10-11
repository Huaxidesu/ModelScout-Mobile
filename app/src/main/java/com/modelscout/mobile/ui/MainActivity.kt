package com.modelscout.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.modelscout.mobile.ModelScoutApp
import com.modelscout.mobile.R
import com.modelscout.mobile.di.AppContainer
import com.modelscout.mobile.keyhub.ui.KeyHubRoot
import com.modelscout.mobile.ui.screens.AboutScreen
import com.modelscout.mobile.ui.screens.ConnectionScreen
import com.modelscout.mobile.ui.screens.ExtractorDialog
import com.modelscout.mobile.ui.screens.PreviewScreen
import com.modelscout.mobile.ui.screens.ResultScreen
import com.modelscout.mobile.ui.screens.RunScreen
import com.modelscout.mobile.ui.theme.ModelScoutTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ModelScoutApp).container
        setContent {
            ModelScoutTheme {
                ModelScoutRoot(container)
            }
        }
    }
}

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem(Screen.CONNECTION, "连接", Icons.Filled.Cable),
    NavItem(Screen.RUN, "运行", Icons.Filled.PlayArrow),
    NavItem(Screen.RESULT, "结果", Icons.Filled.TableChart),
    NavItem(Screen.PREVIEW, "预览", Icons.Filled.Description),
    NavItem(Screen.KEYHUB, "Key 管家", Icons.Filled.VpnKey),
)

private fun titleOf(screen: Screen): String = when (screen) {
    Screen.CONNECTION -> "连接"
    Screen.RUN -> "运行"
    Screen.RESULT -> "结果"
    Screen.PREVIEW -> "预览"
    Screen.ABOUT -> "关于"
    Screen.KEYHUB -> "Key 管家"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScoutRoot(container: AppContainer) {
    val vm: ScanViewModel = viewModel(factory = ScanViewModel.factory(container))
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(titleOf(state.screen))
                        if (state.screen == Screen.KEYHUB) {
                            Spacer(Modifier.width(8.dp))
                            BetaBadge()
                        }
                    }
                },
                navigationIcon = {
                    if (state.screen != Screen.CONNECTION) {
                        IconButton(
                            onClick = {
                                if (state.screen == Screen.ABOUT) vm.closeAbout() else vm.goTo(previousOf(state.screen))
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    // 关于页入口：右上角 GitHub 图标
                    IconButton(onClick = { vm.goTo(Screen.ABOUT) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "关于 / GitHub",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        selected = state.screen == item.screen,
                        onClick = { vm.goTo(item.screen) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.screen) {
                Screen.CONNECTION -> ConnectionScreen(state = state, vm = vm)
                Screen.RUN -> RunScreen(
                    state = state,
                    onAbort = vm::abortScan,
                    onOpenResult = { vm.goTo(Screen.RESULT) },
                )
                Screen.RESULT -> ResultScreen(state = state, vm = vm)
                Screen.PREVIEW -> PreviewScreen(state = state, vm = vm)
                Screen.ABOUT -> AboutScreen()
                Screen.KEYHUB -> KeyHubRoot(store = container.keyHubStore, prefs = container.keyHubPrefs)
            }
        }
    }

    if (state.extractorOpen) {
        ExtractorDialog(state = state, vm = vm)
    }
}

private fun previousOf(screen: Screen): Screen = when (screen) {
    Screen.CONNECTION -> Screen.CONNECTION
    Screen.RUN -> Screen.CONNECTION
    Screen.RESULT -> Screen.RUN
    Screen.PREVIEW -> Screen.RESULT
    Screen.ABOUT -> Screen.CONNECTION
    Screen.KEYHUB -> Screen.CONNECTION
}

/** 测试标识：Key 管家仍是 Beta，功能可能变动。 */
@Composable
private fun BetaBadge() {
    val color = MaterialTheme.colorScheme.secondary
    Text(
        text = "BETA",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
