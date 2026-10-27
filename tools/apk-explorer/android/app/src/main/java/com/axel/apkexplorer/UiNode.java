package com.axel.apkexplorer;

/** Plain snapshot of one accessibility node. No Android types, so the exploration logic is unit-testable on a JVM. */
public final class UiNode {
    public String cls = "", resId = "", text = "", desc = "", label = "";
    public int left, top, right, bottom, depth, window;
    public boolean clickable, scrollable, selected, checked, checkable, enabled = true, editable, password, visible = true;
    /** The node is, or contains, a Switch / ToggleButton / RadioButton / slider: clicking it changes a setting. */
    public boolean toggleControl;
    /** Some node in the subtree (or the node itself) is checkable: switches and checkboxes in Compose are plain Views. */
    public boolean checkableSub;
    /** The node sits inside a clickable container (its text is part of that control, not a heading). */
    public boolean inClickable;
    /** Selected or checked state of the node or of a descendant (filter chips, tabs). */
    public boolean stateOn;
    /** Node is a progress / spinner indicator. */
    public boolean progress;

    public int width() { return Math.max(0, right - left); }
    public int height() { return Math.max(0, bottom - top); }

    public static UiNode clickable(String label, int l, int t, int r, int b) {
        UiNode n = new UiNode();
        n.cls = "android.view.View";
        n.clickable = true;
        n.label = label;
        n.left = l; n.top = t; n.right = r; n.bottom = b;
        return n;
    }

    public static UiNode text(String text, int l, int t, int r, int b) {
        UiNode n = new UiNode();
        n.cls = "android.widget.TextView";
        n.text = text;
        n.left = l; n.top = t; n.right = r; n.bottom = b;
        return n;
    }
}
