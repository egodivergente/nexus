package com.vidadomu.corte.timeline

import com.vidadomu.corte.model.Timeline

/**
 * Undo/redo as a stack of invertible change sets (command pattern on primitive changes). Memory is
 * proportional to what each edit touched, not to project size.
 */
class History(private val limit: Int = 1000) {
    data class Entry(val label: String, val changes: ChangeSet)

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label
    val size: Int get() = undoStack.size

    fun record(result: EditResult.Applied) = record(result.label, result.changes)

    fun record(label: String, changes: ChangeSet) {
        if (changes.isEmpty) return
        undoStack.addLast(Entry(label, changes))
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Returns the timeline after undoing the last edit, or null if there is nothing to undo. */
    fun undo(current: Timeline): Pair<Timeline, String>? {
        val e = undoStack.removeLastOrNull() ?: return null
        val t = e.changes.inverse().applyTo(current)
        redoStack.addLast(e)
        return t to e.label
    }

    fun redo(current: Timeline): Pair<Timeline, String>? {
        val e = redoStack.removeLastOrNull() ?: return null
        val t = e.changes.applyTo(current)
        undoStack.addLast(e)
        return t to e.label
    }

    fun clear() {
        undoStack.clear(); redoStack.clear()
    }
}
