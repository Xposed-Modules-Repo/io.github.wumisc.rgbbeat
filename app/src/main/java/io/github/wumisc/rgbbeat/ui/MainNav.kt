package io.github.wumisc.rgbbeat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs

object MainNavKt {

    @JvmStatic
    fun attachLiquidNav(
        composeView: ComposeView,
        initialSelected: Int,
        onSelect: (Int) -> Unit
    ) {
        composeView.setContent {
            LiquidNavContent(initialSelected, onSelect)
        }
    }

    @Composable
    private fun LiquidNavContent(
        initialSelected: Int,
        onSelect: (Int) -> Unit
    ) {
        var selected by remember { mutableIntStateOf(initialSelected) }
        val backdrop = rememberLayerBackdrop()

        LiquidBottomTabs(
            selectedTabIndex = { selected },
            onTabSelected = { index ->
                selected = index
                onSelect(index)
            },
            backdrop = backdrop,
            tabsCount = 3,
            modifier = Modifier.width(240.dp),
        ) {
            LiquidBottomTab({ selected = 0; onSelect(0) }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("主页", fontSize = 14.sp)
                }
            }
            LiquidBottomTab({ selected = 1; onSelect(1) }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("特效", fontSize = 14.sp)
                }
            }
            LiquidBottomTab({ selected = 2; onSelect(2) }) {
                Box(contentAlignment = Alignment.Center) {
                    Text("关于", fontSize = 14.sp)
                }
            }
        }
    }
}
