package com.cristopbot.estadia

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class BrowsePageModel(
    val isFavoritesPage: Boolean,
    val isSearchPage: Boolean,
    val query: String,
    val category: String,
    val favorites: Set<Int>,
    val sortBy: StaySortOption,
    val results: List<Stay>,
    val favoritesLoaded: Boolean,
    val backendMessage: String?,
    val homeTitle: String?,
    val homeSubtitle: String?,
    val homeAnnouncement: com.cristopbot.estadia.data.PublicScreenConfig.Announcement?,
    val destinations: List<String>
)

internal data class BrowsePageActions(
    val onQueryChange: (String) -> Unit,
    val onCategoryChange: (String) -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenFilters: () -> Unit,
    val onPublish: () -> Unit,
    val onClearFavorites: () -> Unit,
    val onSortChange: (StaySortOption) -> Unit,
    val onToggleFavorite: (Stay) -> Unit,
    val onSelectStay: (Stay) -> Unit
)

@Composable
internal fun BrowsePage(
    model: BrowsePageModel,
    actions: BrowsePageActions,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(Color(0xFFF6F6F5)).testTag("browse-page"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (model.isFavoritesPage) {
            item {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Coral, modifier = Modifier.size(24.dp))
                    Text("Favoritos", Modifier.weight(1f).padding(start = 10.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    if (model.favorites.isNotEmpty()) {
                        IconButton(onClick = actions.onClearFavorites) {
                            Icon(Icons.Default.DeleteOutline, "Quitar todos los favoritos", tint = Muted)
                        }
                    }
                }
            }
        } else if (model.isSearchPage) {
            item { SearchPageHeader(query = model.query, onQuery = actions.onQueryChange, onOpenFilters = actions.onOpenFilters) }
        } else {
            item {
                AlozenaHomeHeader(
                    query = model.query,
                    selectedCategory = model.category,
                    onQuery = actions.onQueryChange,
                    onOpenSettings = actions.onOpenSettings,
                    onCategory = actions.onCategoryChange,
                    onDestination = actions.onQueryChange,
                    onPublish = actions.onPublish,
                    destinations = model.destinations,
                    homeTitle = model.homeTitle,
                    homeSubtitle = model.homeSubtitle
                )
            }
            model.homeAnnouncement?.let { announcement ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        color = Color(0xFFEAF2F7),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(announcement.title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(announcement.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        model.backendMessage?.let { message ->
            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F3F6)
                ) {
                    Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onOpenFilters, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                        Text(" Precio y tipo", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = actions.onOpenFilters, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) {
                        Text("Tipo · ${if (model.category == "Todo") "Todos" else model.category}", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        StaySortOption.RELEVANCE to "Relevancia",
                        StaySortOption.PRICE_ASC to "Precio ↓",
                        StaySortOption.PRICE_DESC to "Precio ↑",
                        StaySortOption.NEWEST to "Nuevos"
                    ).forEach { (option, label) ->
                        FilterChip(
                            selected = model.sortBy == option,
                            onClick = { actions.onSortChange(option) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Coral.copy(alpha = 0.12f),
                                containerColor = Color.White,
                                selectedLabelColor = Coral,
                                labelColor = Ink
                            )
                        )
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (model.isFavoritesPage) "Tus favoritos" else if (model.isSearchPage) "Resultados de búsqueda" else "Destacados",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = Ink
                )
                Text("${model.results.size} publicaciones", color = Muted, fontSize = 12.sp)
            }
        }
        if (!model.favoritesLoaded) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 52.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Coral)
                }
            }
        } else if (model.results.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (model.isFavoritesPage) Icons.Default.FavoriteBorder else Icons.Default.Search, null, tint = Muted, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(if (model.isFavoritesPage) "Aún no tienes favoritos" else "Todavía no hay publicaciones", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(
                        if (model.isFavoritesPage) "Guarda un alojamiento publicado para verlo aquí."
                        else "Sé el primero en publicar un alojamiento real en Alozena.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    if (!model.isFavoritesPage) TextButton(onClick = actions.onPublish) { Text("Publicar alojamiento", color = Coral) }
                }
            }
        } else {
            items(model.results, key = { it.id }) { stay ->
                StayCard(
                    stay = stay,
                    isFavorite = stay.id in model.favorites,
                    onFavorite = { actions.onToggleFavorite(stay) },
                    onClick = { actions.onSelectStay(stay) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}
