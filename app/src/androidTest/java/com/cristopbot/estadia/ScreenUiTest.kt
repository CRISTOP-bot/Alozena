package com.cristopbot.estadia

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.cristopbot.estadia.data.PublicScreenConfig
import com.cristopbot.estadia.ui.SettingsDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ScreenUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browseScreenRendersRemoteContentAndOpensSettings() {
        var settingsOpened = false
        composeRule.setContent {
            AlozenaTheme(1f) {
                BrowsePage(
                    model = BrowsePageModel(
                        isFavoritesPage = false,
                        isSearchPage = false,
                        query = "",
                        category = "Todo",
                        favorites = emptySet(),
                        sortBy = StaySortOption.RELEVANCE,
                        results = emptyList(),
                        favoritesLoaded = true,
                        backendMessage = null,
                        homeTitle = "Título remoto",
                        homeSubtitle = "Subtítulo remoto",
                        homeAnnouncement = PublicScreenConfig.Announcement("Aviso remoto", "Texto de prueba"),
                        destinations = emptyList()
                    ),
                    actions = BrowsePageActions(
                        onQueryChange = {},
                        onCategoryChange = {},
                        onOpenSettings = { settingsOpened = true },
                        onOpenFilters = {},
                        onPublish = {},
                        onClearFavorites = {},
                        onSortChange = {},
                        onToggleFavorite = {},
                        onSelectStay = {}
                    )
                )
            }
        }

        composeRule.onNodeWithText("Título remoto").assertExists()
        composeRule.onNodeWithContentDescription("Configuración").performClick()
        composeRule.runOnIdle { assertTrue(settingsOpened) }
        composeRule.onNodeWithTag("browse-page").performScrollToNode(hasText("Aviso remoto"))
        composeRule.onNodeWithText("Aviso remoto").assertExists()
        composeRule.onNodeWithText("Texto de prueba").assertExists()
    }

    @Test
    fun settingsControlsUpdateRealtimeAndTextPreferences() {
        var realtimeUpdatesEnabled = true
        var selectedTextScale = 1f
        var refreshCount = 0

        composeRule.setContent {
            AlozenaTheme(1f) {
                SettingsDialog(
                    backendReady = true,
                    realtimeUpdatesEnabled = realtimeUpdatesEnabled,
                    onRealtimeUpdatesChange = { realtimeUpdatesEnabled = it },
                    textScale = selectedTextScale,
                    onTextScaleChange = { selectedTextScale = it },
                    screenSyncStatus = "Pantallas sincronizadas",
                    isRefreshing = false,
                    settingsNotice = "Aviso desde el servidor",
                    onRefreshScreens = { refreshCount++ },
                    onOpenTerms = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("realtime-updates-toggle").performClick()
        composeRule.onNodeWithText("Grande").performClick()
        composeRule.onNodeWithText("Actualizar pantallas ahora").performClick()
        composeRule.runOnIdle {
            assertEquals(false, realtimeUpdatesEnabled)
            assertEquals(1.15f, selectedTextScale, 0.001f)
            assertEquals(1, refreshCount)
        }
        composeRule.onNodeWithText("Aviso desde el servidor").assertExists()
    }
}
