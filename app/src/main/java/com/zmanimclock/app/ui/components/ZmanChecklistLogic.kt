package com.zmanimclock.app.ui.components

/**
 * The selection rules behind the zman checklists, kept out of the composables
 * so they can be pinned by plain JVM tests.
 *
 * Two flavours exist because the two screens genuinely differ:
 *  - the widget picker stores an ordered LIST with a hard cap
 *    ([com.zmanimclock.app.feature.widget.WidgetPrefs.MAX_ZMANIM] rows fit),
 *  - the "הזמן הבא" filter stores an unordered SET where the empty set means
 *    "all zmanim" — a meaning that predates this checklist and must survive it.
 */
object ZmanChecklistLogic {

    /**
     * Toggle [name] in an ordered, capped selection.
     *
     * Removing always works. Adding is refused (input returned unchanged) once
     * [max] entries are selected — the UI communicates this by disabling the
     * remaining rows, but the logic must refuse too, so a race between two
     * taps cannot overshoot the cap.
     */
    fun toggleCapped(current: List<String>, name: String, max: Int): List<String> =
        when {
            name in current -> current - name
            current.size < max -> current + name
            else -> current
        }

    /**
     * Toggle [name] in the uncapped filter set. Empty stays a valid result:
     * unchecking the last zman returns the filter to "all", exactly as the
     * old chip UI did.
     */
    fun toggleUncapped(selected: Set<String>, name: String): Set<String> =
        if (name in selected) selected - name else selected + name
}
