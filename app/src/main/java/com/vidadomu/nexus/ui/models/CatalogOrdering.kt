package com.vidadomu.nexus.ui.models

/** Key based: headers, filtering and hidden cards cannot shift numeric item indexes. */
internal fun moveCatalogItem(saved: List<String>, visible: List<String>, from: String, to: String): List<String> {
    if(from !in visible || to !in visible || from == to) return saved
    val moved = visible.toMutableList()
    val destination = moved.indexOf(to)
    moved.remove(from)
    moved.add(destination, from)
    // Keep nonvisible keys so hiding a model does not erase its saved position.
    val complete = (saved + visible).distinct().toMutableList()
    var cursor = 0
    return complete.map { if(it in visible) moved[cursor++] else it }
}
