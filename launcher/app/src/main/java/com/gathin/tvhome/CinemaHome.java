package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Cinema: the focused app's own artwork, washed into a soft full-screen backdrop that slowly zooms (Ken Burns),
 * a giant faint initial, the app's name in large type, and a ribbon of tiles along the bottom with a white ring
 * on the focused one.
 */
final class CinemaHome extends StyleHome {
    private static final int TOP = 0, RIB = 1;
    private static final float TW = 236, TH = 133, STEP = 260, RING = 8;

    private FrameLayout artBox, ribbon;
    private final ImageView[] art = new ImageView[2];
    private int artFront;
    private Drawable[] washes = new Drawable[0];
    private TextView glyph, name, tag;
    private final TextView[] tabs = new TextView[3];
    private final List<FrameLayout> cells = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private int zone = RIB, topIdx, idx, shownArt = -1;

    CinemaHome(Activity a, Host h) {
        super(a, h);
    }

    @Override
    protected void build() {
        artBox = new FrameLayout(act);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1.35f);
        for (int i = 0; i < 2; i++) {
            art[i] = new ImageView(act);
            art[i].setScaleType(ImageView.ScaleType.FIT_XY);
            art[i].setColorFilter(new ColorMatrixColorFilter(sat));
            art[i].setAlpha(0f);
            artBox.addView(art[i], new FrameLayout.LayoutParams(-1, -1));
        }
        add(root, artBox, -40, -40, 1360, 800);

        glyph = text(760, medium, 0x12FFFFFF);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        glp.rightMargin = px(-40);
        glp.topMargin = px(-150);
        root.addView(glyph, glp);

        View scrimL = new View(act);
        scrimL.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF2000000, 0x99000000, 0x00000000}));
        add(root, scrimL, 0, 0, 960, 720);
        View scrimB = new View(act);
        scrimB.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0x66000000, 0x00000000}));
        add(root, scrimB, 0, 324, 1280, 396);

        greetingView = text(14, medium, 0x99FFFFFF);
        greetingView.setLetterSpacing(0.24f);
        add(root, greetingView, 80, 50, -1, -1);

        LinearLayout nav = new LinearLayout(act);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_HORIZONTAL);
        nav.setClipChildren(false);
        CharSequence[] labels = {act.getString(R.string.inputs), host.networkLabel(), act.getString(R.string.settings)};
        for (int i = 0; i < 3; i++) {
            tabs[i] = text(18, medium, 0xB3FFFFFF);
            tabs[i].setText(labels[i]);
            tabs[i].setPadding(px(22), px(10), px(22), px(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = px(10);
            nav.addView(tabs[i], lp);
        }
        add(root, nav, 0, 38, 1280, -1);

        LinearLayout clock = new LinearLayout(act);
        clock.setOrientation(LinearLayout.HORIZONTAL);
        clockView = text(24, light, WHITE);
        ampmView = text(15, regular, 0x99FFFFFF);
        clock.addView(clockView);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.leftMargin = px(6);
        clock.addView(ampmView, alp);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        clp.rightMargin = px(80);
        clp.topMargin = px(48);
        root.addView(clock, clp);

        LinearLayout hero = new LinearLayout(act);
        hero.setOrientation(LinearLayout.VERTICAL);
        dateView = text(13, medium, 0x99FFFFFF);
        dateView.setLetterSpacing(0.34f);
        hero.addView(dateView);
        name = text(104, medium, WHITE);
        name.setLetterSpacing(-0.04f);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
        nlp.topMargin = px(12);
        hero.addView(name, nlp);
        tag = text(21, regular, 0xB3FFFFFF);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = px(18);
        hero.addView(tag, tlp);
        LinearLayout cta = new LinearLayout(act);
        cta.setOrientation(LinearLayout.HORIZONTAL);
        TextView open = text(17, medium, Color.BLACK);
        open.setText(act.getString(R.string.style_open));
        open.setPadding(px(26), px(12), px(26), px(12));
        open.setBackground(rounded(WHITE, 26 * u, 0, 0));
        cta.addView(open);
        TextView more = text(17, medium, WHITE);
        more.setText(R.string.style_up_hint);
        more.setPadding(px(26), px(12), px(26), px(12));
        more.setBackground(rounded(0x24FFFFFF, 26 * u, 0, 0));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.leftMargin = px(12);
        cta.addView(more, mlp);
        LinearLayout.LayoutParams calp = new LinearLayout.LayoutParams(-2, -2);
        calp.topMargin = px(28);
        hero.addView(cta, calp);
        add(root, hero, 80, 176, 720, -1);

        ribbon = new FrameLayout(act);
        ribbon.setClipChildren(false);
        root.setClipChildren(false);
        add(root, ribbon, 80 - RING, 524 - RING, 2000, TH + 2 * RING + 30);
    }

    @Override
    protected void bindApps() {
        ribbon.removeAllViews();
        cells.clear();
        tiles.clear();
        washes = new Drawable[apps.size()];
        shownArt = -1;
        for (int i = 0; i < apps.size(); i++) {
            FrameLayout cell = new FrameLayout(act);
            cell.setClipChildren(false);
            Tile t = tile(i, TW, TH, 16);
            add(cell, t, RING, RING, TW, TH);
            add(ribbon, cell, i * STEP, 0, TW + 2 * RING, TH + 2 * RING);
            cells.add(cell);
            tiles.add(t);
        }
        idx = Math.max(0, Math.min(idx, apps.size() - 1));
        if (apps.isEmpty()) zone = TOP;
        setFocus(zone, zone == TOP ? topIdx : idx, 0, false);
    }

    @Override
    protected void focusFirst() {
        if (!apps.isEmpty()) setFocus(RIB, 0, 0, true);
    }

    @Override
    protected void refocus() {
        setFocus(zone, zone == TOP ? topIdx : idx, 0, false);
    }

    @Override
    protected void refreshNetwork(CharSequence label) {
        if (!String.valueOf(label).contentEquals(tabs[1].getText())) tabs[1].setText(label);
    }

    @Override
    protected boolean key(int k) {
        int n = apps.size();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int dx = k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                if (zone == TOP) {
                    int m = topIdx + dx;
                    if (m < 0 || m > 2) bump(tabs[topIdx], dx, 0);
                    else move(TOP, m, dx);
                } else {
                    int m = idx + dx;
                    if (m < 0 || m >= n) bump(cells.get(idx), dx, 0);
                    else move(RIB, m, dx);
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (zone == TOP) bump(tabs[topIdx], 0, -1);
                else move(TOP, topIdx, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (zone == TOP && n > 0) move(RIB, idx, 0);
                else bump(zone == TOP ? tabs[topIdx] : cells.get(idx), 0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (zone == TOP) {
                    sounds.pop();
                    if (topIdx == 0) host.openInputs();
                    else if (topIdx == 1) host.openNetwork();
                    else host.openSettings();
                } else if (idx < tiles.size()) {
                    launch(idx, tiles.get(idx));
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (zone == TOP && n > 0) move(RIB, idx, 0);
                return true;
            default:
                return false;
        }
    }

    private void move(int z, int i, int dir) {
        sounds.tick();
        setFocus(z, i, dir, true);
    }

    private void setFocus(int z, int i, int dir, boolean animate) {
        boolean anim = animate && motion;
        zone = z;
        if (z == TOP) topIdx = i;
        else idx = i;
        for (int t = 0; t < 3; t++) {
            boolean f = z == TOP && t == topIdx;
            tabs[t].setTextColor(f ? Color.BLACK : 0xB3FFFFFF);
            tabs[t].setBackground(f ? rounded(WHITE, 22 * u, 0, 0) : null);
            float s = f ? 1.06f : 1f;
            if (anim) tabs[t].animate().scaleX(s).scaleY(s).setDuration(400).setInterpolator(new OvershootInterpolator(2f)).start();
            else { tabs[t].setScaleX(s); tabs[t].setScaleY(s); }
        }
        for (int c = 0; c < cells.size(); c++) {
            FrameLayout cell = cells.get(c);
            boolean f = z == RIB && c == idx;
            cell.setForeground(f ? rounded(Color.TRANSPARENT, 22 * u, Math.max(2, px(3)), WHITE) : null);
            float s = f ? 1.08f : 1f, ty = f ? -12 * u : 0;
            if (anim) cell.animate().scaleX(s).scaleY(s).translationY(ty).setDuration(480)
                    .setInterpolator(new OvershootInterpolator(1.4f)).start();
            else { cell.setScaleX(s); cell.setScaleY(s); cell.setTranslationY(ty); }
        }
        ribbon.animate().alpha(z == TOP ? 0.45f : 1f).setDuration(anim ? 400 : 0).start();
        int n = apps.size();
        float tx = -Math.max(0, Math.min(idx - 2, n - 5)) * STEP * u;
        if (anim) ribbon.animate().translationX(tx).setDuration(600).setInterpolator(SMOOTH).start();
        else ribbon.setTranslationX(tx);
        if (idx < tiles.size()) {
            if (z == RIB && anim) tiles.get(idx).sweep(true);
            String label = labelOf(idx);
            swapText(name, label, -18, 0, anim);
            tag.setText(openedLine(idx));
            showArt(idx, anim);
            String initial = label.isEmpty() ? "" : label.substring(0, 1).toUpperCase(Locale.getDefault());
            if (!initial.contentEquals(glyph.getText())) {
                if (anim) glyph.animate().alpha(0f).setDuration(220).withEndAction(() -> {
                    glyph.setText(initial);
                    glyph.animate().alpha(1f).setDuration(500).start();
                }).start();
                else glyph.setText(initial);
            }
        }
    }

    /** Cross-fades the backdrop to app i's washed artwork (made once per app, then kept). */
    private void showArt(int i, boolean anim) {
        if (i == shownArt || i >= washes.length) return;
        shownArt = i;
        if (washes[i] == null) washes[i] = wash(i);
        artFront ^= 1;
        ImageView in = art[artFront], out = art[artFront ^ 1];
        in.setImageDrawable(washes[i]);
        in.bringToFront();
        if (anim) {
            in.setAlpha(0f);
            in.animate().alpha(1f).setDuration(900).withEndAction(() -> out.setAlpha(0f)).start();
        } else {
            in.setAlpha(1f);
            out.setAlpha(0f);
        }
    }

    @Override
    protected void startAmbient() {
        if (motion) kenBurns(true);
    }

    @Override
    protected void stopAmbient() {
        artBox.animate().cancel();
    }

    private void kenBurns(boolean in) {
        artBox.animate().scaleX(in ? 1.1f : 1f).scaleY(in ? 1.1f : 1f)
                .translationX(in ? -30 * u : 0).translationY(in ? 10 * u : 0)
                .setDuration(26000).setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> kenBurns(!in)).start();
    }
}
