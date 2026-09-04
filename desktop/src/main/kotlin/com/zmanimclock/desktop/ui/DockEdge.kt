package com.zmanimclock.desktop.ui

/**
 * Which screen edge the folded bookmark — and the panel it opens into — is
 * attached to.
 *
 * Every place that draws or positions the bookmark or the open panel branches
 * on this, always the same way: one dimension is FIXED (the bookmark's
 * thickness; the panel's width or height, whichever tracks the tab's own
 * measured width) and flush against the edge, the other dimension runs the
 * FULL LENGTH of the screen along that edge. LEFT/RIGHT fix width and run the
 * full height; TOP/BOTTOM fix height and run the full width — the same rule,
 * rotated 90°.
 */
internal enum class DockEdge {
    LEFT, RIGHT, TOP, BOTTOM;

    /** True for the two edges that run vertically (the screen's sides). */
    val isVertical: Boolean get() = this == LEFT || this == RIGHT
}
