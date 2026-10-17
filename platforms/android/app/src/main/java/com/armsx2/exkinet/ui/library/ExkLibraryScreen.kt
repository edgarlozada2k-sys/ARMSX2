package com.armsx2.exkinet.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.armsx2.GameInfo
import com.armsx2.PlayTime
import com.armsx2.exkinet.data.ExkStore
import com.armsx2.exkinet.ui.ExkChoice
import com.armsx2.exkinet.ui.ExkSecondaryButton
import com.armsx2.exkinet.ui.LocalExk
import com.armsx2.exkinet.ui.exkNav
import com.armsx2.exkinet.ui.home.ExkCover
import com.armsx2.exkinet.ui.home.ExkTab
import com.armsx2.exkinet.ui.home.ExkTopBar
import com.armsx2.exkinet.ui.home.relative

enum class LibFilter { ALL, FAVORITES, RECENT }
enum class LibSort { RECENT, NAME, PLAYTIME }

object ExkLibraryState {
    val query = mutableStateOf("")
    val sort = mutableStateOf(LibSort.RECENT)
}

@Composable
fun ExkLibraryScreen(
    games: List<GameInfo>,
    favoritesOnly: Boolean,
    scanning: Boolean,
    focusSearch: Boolean,
    onTab: (ExkTab) -> Unit,
    onSelect: (GameInfo) -> Unit,
    onEdit: (GameInfo) -> Unit,
    onAddFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onRescan: () -> Unit,
) {
    val c = LocalExk.current
    @Suppress("UNUSED_EXPRESSION") ExkStore.version.intValue
    var query by ExkLibraryState.query
    var sort by ExkLibraryState.sort
    var filter by remember(favoritesOnly) { mutableStateOf(if (favoritesOnly) LibFilter.FAVORITES else LibFilter.ALL) }
    val en = com.armsx2.EnglishTitles.enabled.value
    val list = games
        .filter { g ->
            when (filter) {
                LibFilter.ALL -> true
                LibFilter.FAVORITES -> ExkStore.isFavorite(g)
                LibFilter.RECENT -> PlayTime.lastPlayedMillis(g.serial) > 0
            }
        }
        .filter { query.isBlank() || it.displayTitle(en).contains(query, true) || (it.serial ?: "").contains(query, true) }
        .let { l ->
            when (sort) {
                LibSort.RECENT -> l.sortedWith(compareByDescending<GameInfo> { PlayTime.lastPlayedMillis(it.serial) }.thenBy { it.sortKey(en).lowercase() })
                LibSort.NAME -> l.sortedBy { it.sortKey(en).lowercase() }
                LibSort.PLAYTIME -> l.sortedByDescending { PlayTime.playedSeconds(it.serial) }
            }
        }

    Box(Modifier.fillMaxSize().background(c.screenBrush)) {
        Column(Modifier.fillMaxSize()) {
            ExkTopBar(if (favoritesOnly) ExkTab.FAVORITES else ExkTab.LIBRARY, onTab, onSearch = {}, transparent = false)
            Column(Modifier.padding(horizontal = 64.dp)) {
                Text(if (favoritesOnly) "TUS FAVORITOS" else "TU UNIVERSO, ORGANIZADO", color = c.accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text(if (favoritesOnly) "Favoritos" else "Biblioteca", color = c.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.width(420.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF20232F))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)).padding(14.dp),
                    ) {
                        if (query.isEmpty()) Text("Buscar por nombre o serial…", color = c.textMuted, fontSize = 15.sp)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                            cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                    }
                    ExkChoice(listOf(LibFilter.ALL to "Todos", LibFilter.FAVORITES to "Favoritos", LibFilter.RECENT to "Recientes"), filter, { filter = it }, navBase = "exk.lib.filter")
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ordenar:", color = c.textMuted, fontSize = 13.sp)
                    ExkChoice(listOf(LibSort.RECENT to "Recientes", LibSort.NAME to "Nombre", LibSort.PLAYTIME to "Tiempo jugado"), sort, { sort = it }, navBase = "exk.lib.sort")
                    Spacer(Modifier.weight(1f))
                    ExkSecondaryButton("＋ Carpeta", onAddFolder, compact = true, navId = "exk.lib.addfolder")
                    ExkSecondaryButton("Abrir archivo", onOpenFile, compact = true, navId = "exk.lib.openfile")
                    ExkSecondaryButton(if (scanning) "Buscando…" else "Actualizar", onRescan, compact = true, enabled = !scanning, navId = "exk.lib.rescan")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) {
                Text(
                    when {
                        games.isEmpty() -> "Todavía no hay juegos. Añade la carpeta donde los guardas."
                        filter == LibFilter.FAVORITES -> "No hay favoritos. Toca el corazón de un juego en Inicio."
                        else -> "Ningún juego coincide con la búsqueda."
                    },
                    color = c.textMuted, fontSize = 15.sp, modifier = Modifier.padding(64.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 14.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { ExkStore.key(it) }) { g -> LibraryCard(g, en, onSelect, onEdit) }
            }
        }
    }
}

@Composable
private fun LibraryCard(g: GameInfo, en: Boolean, onSelect: (GameInfo) -> Unit, onEdit: (GameInfo) -> Unit) {
    val c = LocalExk.current
    val shape = RoundedCornerShape(12.dp)
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f).clip(shape).border(2.dp, Color(0x12FFFFFF), shape)
                .exkNav("exk.lib.card.${ExkStore.key(g)}", { onSelect(g) }, shape = shape)
                .clickable { onSelect(g) },
        ) {
            ExkCover(g, Modifier.fillMaxSize())
            if (ExkStore.isFavorite(g)) Text("♥", color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.displayTitle(en), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val last = PlayTime.lastPlayedMillis(g.serial)
                Text(if (last > 0) "Última partida · ${relative(last)}" else "Listo para jugar", color = c.textMuted, fontSize = 11.sp)
            }
            Text("✎", color = c.textSecondary, fontSize = 16.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onEdit(g) }.padding(6.dp))
        }
    }
}
