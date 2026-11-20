package com.anasdev.pdfreader.domain.model

/**
 * State representing PDF in-document text search.
 *
 * @param isSearchOpen True when the search bar/mode is currently active
 * @param query Current search term
 * @param isSearching True while background IO coroutine is scanning pages
 * @param matches Ordered list of all discovered matches in document reading order
 * @param currentMatchIndex Index in [matches] of the currently selected/focused match (-1 if none)
 * @param matchesByPage Fast O(1) lookup of matches grouped by page index for overlay rendering
 */
data class SearchUiState(
    val isSearchOpen: Boolean = false,
    val query: String = "",
    val isSearching: Boolean = false,
    val matches: List<SearchMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val matchesByPage: Map<Int, List<SearchMatch>> = emptyMap()
) {
    val totalMatches: Int get() = matches.size
    val currentMatchNumber: Int get() = if (currentMatchIndex >= 0 && matches.isNotEmpty()) currentMatchIndex + 1 else 0
    val currentMatch: SearchMatch? get() = matches.getOrNull(currentMatchIndex)
}
