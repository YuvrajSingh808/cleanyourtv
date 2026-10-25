package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Orbit: the apps on a curved 3D carousel with mirror reflections, over a quietly twinkling star field and a
 * horizon that glows in the focused app's colour. Left and Right turn the carousel; Up reaches the top bar.
 */
final class OrbitHome extends StyleHome {
    private static final int TOP = 0, ORB = 1;
    private static final int BG = 0xFF02030A;
    private static final float TW = 360, TH = 202, PERSPECTIVE = 1100;

    private StarsView stars;
    private View horizon;
    private GradientDrawable horizonGlow;
    private int horizonColor = 0xFF333333;
    private ValueAnimator colorAnim;
    private FrameLayout orbit;
    private TextView oname, osub, headline;
    private LinearLayout dots;
    private final TextView[] pills = new TextView[3];
    private final List<FrameLayout> items = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private int zone = ORB, topIdx, idx;

    OrbitHome(Activity a, Host h) {
        super(a, h);
    }

    @Override
    protected void build() {
        root.setBackgroundColor(BG);
        stars = new StarsView(act, u, motion);
        root.addView(stars, new FrameLayout.LayoutParams(-1, -1));

        horizon = new View(act);
        horizonGlow = new GradientDrawable();
        horizonGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        horizonGlow.setGradientRadius(960 * u);
        setHorizon(horizonColor);
        horizon.setBackground(horizonGlow);
        add(root, horizon, -320, 778 - 960, 1920, 1920);
        horizon.setScaleY(0.36f);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        // Full-width rows with centred text: a wrap-content clock measured before its first text came up
        // at the left edge, half off screen.
        clockView = text(76, thin, WHITE);
        clockView.setLetterSpacing(-0.03f);
        clockView.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(clockView, new LinearLayout.LayoutParams(-1, -2));
        // Greeting and date as one centred line (updateClock below): a separate one-glyph view for the dot measured
        // 2 px wide and drew a clipped smudge.
        headline = text(14, medium, 0x8CFFFFFF);
        headline.setLetterSpacing(0.3f);
        headline.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.topMargin = px(16);
        head.addView(headline, llp);
        add(root, head, 0, 44, 1280, -1);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        pills[0] = pill(R.drawable.ic_input, act.getString(R.string.inputs));
        pills[1] = pill(R.drawable.ic_wifi, null);
        pills[2] = pill(R.drawable.ic_settings, null);
        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == 0 ? -2 : px(50), px(50));
            if (i > 0) lp.leftMargin = px(12);
            bar.addView(pills[i], lp);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.topMargin = px(44);
        blp.rightMargin = px(72);
        root.addView(bar, blp);

        orbit = new FrameLayout(act);
        orbit.setClipChildren(false);
        root.setClipChildren(false);
        add(root, orbit, 0, 240, 1280, 340);

        oname = text(44, light, WHITE);
        addCentered(root, oname, 532);
        osub = text(15, regular, 0x80FFFFFF);
        osub.setLetterSpacing(0.06f);
        addCentered(root, osub, 590);
        dots = new LinearLayout(act);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER_HORIZONTAL);
        add(root, dots, 0, 640, 1280, 8);
    }

    @Override
    protected void bindApps() {
        orbit.removeAllViews();
        dots.removeAllViews();
        items.clear();
        tiles.clear();
        float camera = PERSPECTIVE * u * act.getResources().getDisplayMetrics().densityDpi / 72f;
        for (int i = 0; i < apps.size(); i++) {
            FrameLayout item = new FrameLayout(act);
            item.setClipChildren(false);
            item.setCameraDistance(camera);
            Tile t = tile(i, TW, TH, 22);
            add(item, t, 0, 0, TW, TH);
            // The reflection: the same tile mirrored about its middle, of which the container shows the
            // bottom half, faded into the background.
            FrameLayout mirror = new FrameLayout(act);
            mirror.setAlpha(0.22f);
            Tile r = tile(i, TW, TH, 22);
            add(mirror, r, 0, 0, TW, TH);
            r.setPivotY(TH / 2f * u);
            r.setScaleY(-1f);
            View fade = new View(act);
            fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{withAlpha(BG, 0x40), BG}));
            mirror.addView(fade, new FrameLayout.LayoutParams(-1, -1));
            add(item, mirror, 0, TH + 10, TW, TH / 2f);
            add(orbit, item, 460, 20, TW, TH + 10 + TH / 2f);
            items.add(item);
            tiles.add(t);
            View d = new View(act);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(7), px(7));
            dlp.leftMargin = dlp.rightMargin = px(4.5f);
            dots.addView(d, dlp);
        }
        idx = Math.max(0, Math.min(idx, apps.size() - 1));
        if (apps.isEmpty()) zone = TOP;
        setFocus(zone, zone == TOP ? topIdx : idx, false);
    }

    @Override
    protected void updateClock() {
        super.updateClock();
        if (headline == null) return;
        Locale l = Locale.getDefault();
        headline.setText(String.valueOf(host.greeting()).toUpperCase(l) + "   \u00B7   "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", l)).toUpperCase(l));
    }

    @Override
    protected void focusFirst() {
        if (!apps.isEmpty()) setFocus(ORB, 0, true);
    }

    @Override
    protected void refocus() {
        setFocus(zone, zone == TOP ? topIdx : idx, false);
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
                    if (m < 0 || m > 2) bump(pills[topIdx], dx, 0);
                    else move(TOP, m);
                } else {
                    int m = idx + dx;
                    if (m < 0 || m >= n) bump(tiles.get(idx), dx, 0);
                    else move(ORB, m);
                }
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (zone == TOP) bump(pills[topIdx], 0, -1);
                else move(TOP, topIdx);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (zone == TOP && n > 0) move(ORB, idx);
                else bump(zone == TOP ? pills[topIdx] : tiles.get(idx), 0, 1);
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
                if (zone == TOP && n > 0) move(ORB, idx);
                return true;
            default:
                return false;
        }
    }

    private void move(int z, int i) {
        sounds.tick();
        setFocus(z, i, true);
    }

    private void setFocus(int z, int i, boolean animate) {
        boolean anim = animate && motion;
        zone = z;
        if (z == TOP) topIdx = i;
        else idx = i;
        for (int p = 0; p < 3; p++) focusPill(pills[p], z == TOP && p == topIdx);
        int n = items.size();
        // Far tiles first, so nearer ones draw over them.
        for (int d = n; d >= 0; d--) {
            for (int c = 0; c < n; c++) if (Math.abs(c - idx) == d) items.get(c).bringToFront();
        }
        for (int c = 0; c < n; c++) {
            int o = c - idx, d = Math.abs(o), s = Integer.signum(o);
            float x = (d <= 1 ? o * 330 : s * (330 + (d - 1) * 150)) * u;
            float sc = PERSPECTIVE / (PERSPECTIVE + 230 * d);
            float ry = -s * Math.min(d * 32, 62);
            float alpha = d > 3 ? 0f : 1f - d * 0.18f;
            FrameLayout item = items.get(c);
            Tile t = tiles.get(c);
            boolean f = d == 0 && z == ORB;
            glow(t, t.color);
            t.setElevation(f ? 24 * u : 0);
            t.setForeground(d == 0 ? rounded(Color.TRANSPARENT, 22 * u, Math.max(2, px(2)), 0x80FFFFFF) : null);
            if (anim) {
                item.animate().translationX(x).scaleX(sc).scaleY(sc).rotationY(ry).alpha(alpha)
                        .setDuration(700).setInterpolator(new OvershootInterpolator(0.8f)).start();
                t.dim.animate().alpha(d * 0.28f).setDuration(500).start();
            } else {
                item.setTranslationX(x);
                item.setScaleX(sc);
                item.setScaleY(sc);
                item.setRotationY(ry);
                item.setAlpha(alpha);
                t.dim.setAlpha(d * 0.28f);
            }
            View dot = dots.getChildAt(c);
            LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            dlp.width = px(c == idx ? 26 : 7);
            dot.setLayoutParams(dlp);
            dot.setBackground(rounded(c == idx ? WHITE : 0x4DFFFFFF, 4 * u, 0, 0));
        }
        orbit.animate().alpha(z == TOP ? 0.5f : 1f).setDuration(anim ? 400 : 0).start();
        if (idx < n) {
            if (z == ORB && anim) tiles.get(idx).sweep(true);
            swapText(oname, labelOf(idx), 0, 10, anim);
            osub.setText(openedLine(idx));
            glowTo(colorOf(idx), anim);
        }
    }

    private void glowTo(int color, boolean animate) {
        if (colorAnim != null) colorAnim.cancel();
        if (!animate) {
            setHorizon(color);
            return;
        }
        final int from = horizonColor;
        final ArgbEvaluator ev = new ArgbEvaluator();
        colorAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(1200);
        colorAnim.addUpdateListener(va -> setHorizon((int) ev.evaluate((float) va.getAnimatedValue(), from, color)));
        colorAnim.start();
    }

    private void setHorizon(int c) {
        horizonColor = c;
        horizonGlow.setColors(new int[]{withAlpha(c, 0xE6), withAlpha(c, 0x73), withAlpha(c, 0x00)});
    }

    @Override
    protected void startAmbient() {
        stars.setRunning(true);
    }

    @Override
    protected void stopAmbient() {
        stars.setRunning(false);
        if (colorAnim != null) colorAnim.end();
    }

    /** About 140 stars, each fading in and out on its own slow cycle; redrawn a dozen times a second at most. */
    private static final class StarsView extends View {
        private final float[] x, y, r, phase;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean motion;
        private boolean running;

        StarsView(Context c, float u, boolean motion) {
            super(c);
            this.motion = motion;
            Random rnd = new Random(7);
            int n = 140;
            x = new float[n];
            y = new float[n];
            r = new float[n];
            phase = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = rnd.nextFloat() * 1280 * u;
                y[i] = rnd.nextFloat() * 520 * u;
                r[i] = (rnd.nextFloat() < 0.15f ? 1.5f : 1f) * u;
                phase[i] = rnd.nextFloat() * 6.283f;
            }
            paint.setColor(Color.WHITE);
        }

        void setRunning(boolean on) {
            running = on && motion;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            double t = SystemClock.uptimeMillis() / 4000.0 * Math.PI;
            for (int i = 0; i < x.length; i++) {
                float a = motion ? 0.15f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t + phase[i])) : 0.45f;
                paint.setAlpha(Math.round(a * 255));
                canvas.drawCircle(x[i], y[i], r[i], paint);
            }
            if (running) postInvalidateDelayed(80);
        }
    }
}
