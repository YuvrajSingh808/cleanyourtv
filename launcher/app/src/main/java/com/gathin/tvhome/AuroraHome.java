package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Aurora Glass: soft light in the focused app's colours drifts behind a frosted dock; the focused tile springs up
 * with a slight 3D tilt in the direction of travel, a sheen sweeps across it and it casts a glow in its own colour.
 * The grid below the dock scrolls up one row per Down.
 */
final class AuroraHome extends StyleHome {
    private static final int TOP = 0, DOCK = 1, GRID = 2;
    private static final float GAP = 28, ROW_W = 1112, LEFT = 84, BAND_TOP = 466;

    private final ImageView[] blobs = new ImageView[3];
    private final int[] blobColors = {0xFF333333, 0xFF222222, 0xFF333333};
    private ValueAnimator colorAnim;
    private FrameLayout scroll, heroBox;
    private TextView title, sub, gridLabel;
    private View band;
    private final TextView[] pills = new TextView[3];
    private final List<Tile> tiles = new ArrayList<>();
    private int cols, dock;
    private float tw, th, gridTop, pitch;
    private int zone = DOCK, topIdx, dockIdx, gridIdx;
    private Tile focused;

    AuroraHome(Activity a, Host h) {
        super(a, h);
    }

    @Override
    protected void build() {
        Bitmap disc = softDisc();
        float[][] geo = {{-260, -320, 900, 800}, {800, 60, 800, 800}, {180, 600, 1000, 600}};
        float[] alpha = {0.75f, 0.55f, 0.45f};
        for (int i = 0; i < 3; i++) {
            ImageView b = new ImageView(act);
            b.setImageBitmap(disc);
            b.setScaleType(ImageView.ScaleType.FIT_XY);
            b.setAlpha(alpha[i]);
            b.setColorFilter(new PorterDuffColorFilter(blobColors[i], PorterDuff.Mode.SRC_IN));
            blobs[i] = add(root, b, geo[i][0], geo[i][1], geo[i][2], geo[i][3]);
        }
        View vignette = new View(act);
        GradientDrawable vg = new GradientDrawable();
        vg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        vg.setColors(new int[]{0x00000000, 0x33000000, 0xC0000000});
        vg.setGradientCenter(0.4f, 0.4f);
        vg.setGradientRadius(900 * u);
        vignette.setBackground(vg);
        root.addView(vignette, new FrameLayout.LayoutParams(-1, -1));

        scroll = new FrameLayout(act);
        scroll.setClipChildren(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        greetingView = text(13, medium, 0x99FFFFFF);
        greetingView.setLetterSpacing(0.34f);
        add(scroll, greetingView, 82, 50, -1, -1);
        LinearLayout clockRow = new LinearLayout(act);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockView = text(116, thin, WHITE);
        clockView.setLetterSpacing(-0.04f);
        ampmView = text(22, regular, 0x99FFFFFF);
        ampmView.setLetterSpacing(0.1f);
        clockRow.addView(clockView);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.leftMargin = px(10);
        clockRow.addView(ampmView, alp);
        add(scroll, clockRow, 74, 66, -1, -1);
        dateView = text(15, regular, 0x8CFFFFFF);
        dateView.setLetterSpacing(0.28f);
        add(scroll, dateView, 82, 194, -1, -1);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        pills[0] = pill(R.drawable.ic_input, act.getString(R.string.inputs));
        pills[1] = pill(R.drawable.ic_wifi, host.networkLabel());
        pills[2] = pill(R.drawable.ic_settings, act.getString(R.string.settings));
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(50));
            if (i > 0) lp.leftMargin = px(14);
            bar.addView(pills[i], lp);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.topMargin = px(62);
        blp.rightMargin = px(72);
        scroll.addView(bar, blp);

        heroBox = new FrameLayout(act);
        scroll.addView(heroBox, new FrameLayout.LayoutParams(-1, -1));
        title = text(78, light, WHITE);
        title.setLetterSpacing(-0.025f);
        add(heroBox, title, 80, 318, -1, -1);
        sub = text(16, regular, 0x8CFFFFFF);
        sub.setLetterSpacing(0.04f);
        add(heroBox, sub, 84, 410, -1, -1);

        band = new View(act);
        band.setBackground(rounded(0x12FFFFFF, 32 * u, Math.max(1, px(1)), 0x1FFFFFFF));
        add(scroll, band, 58, BAND_TOP, 1164, 160);

        gridLabel = text(15, medium, WHITE);
        gridLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        gridLabel.setAlpha(0f);
        add(scroll, gridLabel, 0, 0, 200, -1);
    }

    @Override
    protected void bindApps() {
        for (Tile t : tiles) scroll.removeView(t);
        tiles.clear();
        focused = null;
        cols = Math.max(4, Math.min(7, host.dockSize()));
        dock = Math.min(cols, apps.size());
        tw = (ROW_W - (cols - 1) * GAP) / cols;
        th = tw * 9f / 16f;
        float bandH = th + 48;
        FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) band.getLayoutParams();
        bl.height = px(bandH);
        band.setLayoutParams(bl);
        band.setVisibility(dock > 0 ? View.VISIBLE : View.GONE);
        gridTop = BAND_TOP + bandH + 46;
        pitch = th + 62;
        FrameLayout.LayoutParams gl = (FrameLayout.LayoutParams) gridLabel.getLayoutParams();
        gl.width = px(tw);
        gridLabel.setLayoutParams(gl);
        float camera = 900 * u * act.getResources().getDisplayMetrics().densityDpi / 72f;
        for (int i = 0; i < apps.size(); i++) {
            Tile t = tile(i, tw, th, 16);
            t.setCameraDistance(camera);
            float x, y;
            if (i < dock) {
                x = LEFT + i * (tw + GAP);
                y = BAND_TOP + 24;
            } else {
                int g = i - dock;
                x = LEFT + (g % cols) * (tw + GAP);
                y = gridTop + (g / cols) * pitch;
            }
            tiles.add(add(scroll, t, x, y, tw, th));
        }
        int g = apps.size() - dock;
        dockIdx = Math.max(0, Math.min(dockIdx, dock - 1));
        gridIdx = Math.max(0, Math.min(gridIdx, g - 1));
        if (zone == GRID && g <= 0) zone = DOCK;
        if (zone == DOCK && dock == 0) zone = TOP;
        setFocus(zone, zone == TOP ? topIdx : zone == DOCK ? dockIdx : gridIdx, 0, false);
    }

    @Override
    protected void focusFirst() {
        if (dock > 0) setFocus(DOCK, 0, 0, true);
    }

    @Override
    protected void refocus() {
        setFocus(zone, zone == TOP ? topIdx : zone == DOCK ? dockIdx : gridIdx, 0, false);
    }

    @Override
    protected void refreshNetwork(CharSequence label) {
        if (!String.valueOf(label).contentEquals(pills[1].getText())) pills[1].setText(label);
    }

    @Override
    protected boolean key(int k) {
        int g = apps.size() - dock;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dx = k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                if (zone == TOP) {
                    int n = topIdx + dx;
                    if (n < 0 || n > 2) bump(pills[topIdx], dx, 0);
                    else move(TOP, n, dx);
                } else if (zone == DOCK) {
                    int n = dockIdx + dx;
                    if (n < 0 || n >= dock) bump(focused, dx, 0);
                    else move(DOCK, n, dx);
                } else {
                    int rowStart = gridIdx - gridIdx % cols, n = gridIdx + dx;
                    if (n < rowStart || n >= Math.min(rowStart + cols, g)) bump(focused, dx, 0);
                    else move(GRID, n, dx);
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (zone == TOP) bump(pills[topIdx], 0, -1);
                else if (zone == DOCK) move(TOP, topIdx, 0);
                else if (gridIdx < cols) move(DOCK, Math.min(gridIdx, dock - 1), 0);
                else move(GRID, gridIdx - cols, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (zone == TOP) {
                    if (dock > 0) move(DOCK, dockIdx, 0);
                    else bump(pills[topIdx], 0, 1);
                } else if (zone == DOCK) {
                    if (g > 0) move(GRID, Math.min(dockIdx, Math.min(cols, g) - 1), 0);
                    else bump(focused, 0, 1);
                } else {
                    int next = gridIdx + cols;
                    if (next < g) move(GRID, next, 0);
                    else if ((gridIdx / cols + 1) * cols < g) move(GRID, g - 1, 0);
                    else bump(focused, 0, 1);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (zone == TOP) {
                    sounds.pop();
                    if (topIdx == 0) host.openInputs();
                    else if (topIdx == 1) host.openNetwork();
                    else host.openSettings();
                } else if (focused != null) {
                    launch(focused.index, focused);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (zone != DOCK && dock > 0) move(DOCK, dockIdx, 0);
                return true;
            default:
                return false;
        }
    }

    private void move(int z, int idx, int dir) {
        sounds.tick();
        setFocus(z, idx, dir, true);
    }

    private void setFocus(int z, int idx, int dir, boolean animate) {
        boolean anim = animate && motion;
        if (focused != null) {
            Tile old = focused;
            old.setElevation(0);
            old.setForeground(null);
            if (anim) old.animate().scaleX(1f).scaleY(1f).rotationY(0).setDuration(320).setInterpolator(SMOOTH).start();
            else { old.setScaleX(1f); old.setScaleY(1f); old.setRotationY(0); }
        }
        for (int i = 0; i < 3; i++) focusPill(pills[i], z == TOP && i == idx);
        zone = z;
        focused = null;
        if (z == TOP) topIdx = idx;
        else if (z == DOCK) dockIdx = idx;
        else gridIdx = idx;
        int index = z == DOCK ? idx : z == GRID ? dock + idx : -1;
        if (index >= 0 && index < tiles.size()) {
            Tile t = tiles.get(index);
            focused = t;
            t.bringToFront();
            glow(t, t.color);
            t.setElevation(18 * u);
            t.setForeground(rounded(Color.TRANSPARENT, 16 * u, Math.max(2, px(1.5f)), 0x8CFFFFFF));
            if (anim) {
                if (dir != 0) {
                    t.setRotationY(-dir * 12f);
                    t.setTranslationX(dir * 6 * u);
                }
                t.animate().scaleX(1.14f).scaleY(1.14f).rotationY(0).translationX(0).setDuration(560)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
                t.sweep(true);
            } else {
                t.setScaleX(1.14f);
                t.setScaleY(1.14f);
            }
            tint(t.color, anim);
            swapText(title, labelOf(index), 0, 14, anim);
            String opened = openedLine(index);
            boolean never = opened.equals(act.getString(R.string.style_ok_to_open));
            sub.setText(never ? opened : opened + "   \u00B7   " + act.getString(R.string.style_ok_hint));
        }
        if (z == GRID && focused != null) {
            gridLabel.setText(labelOf(index));
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) focused.getLayoutParams();
            gridLabel.setTranslationX(lp.leftMargin);
            gridLabel.setTranslationY(lp.topMargin + th * u * 1.07f + 10 * u);
            gridLabel.animate().alpha(1f).setDuration(anim ? 250 : 0).start();
        } else {
            gridLabel.animate().alpha(0f).setDuration(anim ? 150 : 0).start();
        }
        heroBox.animate().alpha(z == GRID ? 0f : 1f).setDuration(anim ? 300 : 0).start();
        float ty = z == GRID ? -(gridTop + (gridIdx / cols) * pitch - 372) * u : 0;
        if (anim) scroll.animate().translationY(ty).setDuration(700).setInterpolator(SMOOTH).start();
        else scroll.setTranslationY(ty);
    }

    /** Moves the three lights to the focused app's colour, its darker shade in the middle one. */
    private void tint(int color, boolean animate) {
        final int[] to = {color, darken(color, 0.55f), color};
        final int[] from = blobColors.clone();
        if (colorAnim != null) colorAnim.cancel();
        if (!animate) {
            for (int i = 0; i < 3; i++) setBlob(i, to[i]);
            return;
        }
        final ArgbEvaluator ev = new ArgbEvaluator();
        colorAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(1200);
        colorAnim.addUpdateListener(va -> {
            float p = (float) va.getAnimatedValue();
            for (int i = 0; i < 3; i++) setBlob(i, (int) ev.evaluate(p, from[i], to[i]));
        });
        colorAnim.start();
    }

    private void setBlob(int i, int c) {
        blobColors[i] = c;
        blobs[i].setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void startAmbient() {
        if (!motion) return;
        drift(blobs[0], 180, 140, 1.2f, 21000, true);
        drift(blobs[1], -220, -60, 0.85f, 25000, true);
        drift(blobs[2], -160, -90, 1.15f, 18000, true);
    }

    @Override
    protected void stopAmbient() {
        for (ImageView b : blobs) b.animate().cancel();
        if (colorAnim != null) colorAnim.end();
    }

    /** Slowly to an offset and back, forever; property animations only, nothing is redrawn but the frame. */
    private void drift(View v, float dx, float dy, float s, long ms, boolean out) {
        v.animate().translationX(out ? dx * u : 0).translationY(out ? dy * u : 0)
                .scaleX(out ? s : 1f).scaleY(out ? s : 1f).setDuration(ms)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> drift(v, dx, dy, s, ms, !out)).start();
    }
}
