package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An alternative Home layout drawn over MainActivity (home_style: aurora, cinema or orbit; classic is MainActivity
 * itself). MainActivity keeps loading the apps, owning Home and running the remote helpers; a style only reads the
 * loaded tiles from AppAdapter, draws its own screen and takes the D-pad. Inputs, the network pill and Settings go
 * through MainActivity's own buttons, so they behave exactly as in the classic layout.
 *
 * Layouts are designed on a 1280x720 canvas and scaled to the screen with {@link #u}.
 */
abstract class StyleHome {
    static final String KEY = "home_style";
    static final String SOUNDS_KEY = "ui_sounds";
    static final String CLASSIC = "classic", AURORA = "aurora", CINEMA = "cinema", ORBIT = "orbit";
    static final String[] STYLES = {CLASSIC, AURORA, CINEMA, ORBIT};
    private static final String STATE = "style_state";

    /** What a style needs from MainActivity. */
    interface Host {
        AppAdapter adapter();
        /** Apps in the dock row (AppAdapter's dock), for styles with a dock. */
        int dockSize();
        void openInputs();
        void openNetwork();
        void openSettings();
        CharSequence networkLabel();
        CharSequence greeting();
        boolean animations();
    }

    static String chosen(Context c) {
        String fallback = c.getString(R.string.home_style);
        String s = c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, fallback);
        for (String k : STYLES) if (k.equals(s)) return s;
        return CLASSIC;
    }

    static boolean soundsOn(Context c) {
        return c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(SOUNDS_KEY, c.getResources().getBoolean(R.bool.ui_sounds));
    }

    /** The chosen style, or null for the classic layout. */
    static StyleHome create(Activity a, Host h) {
        switch (chosen(a)) {
            case AURORA: return new AuroraHome(a, h);
            case CINEMA: return new CinemaHome(a, h);
            case ORBIT: return new OrbitHome(a, h);
            default: return null;
        }
    }

    protected static final int WHITE = Color.WHITE;
    protected static final PathInterpolator SMOOTH = new PathInterpolator(0.2f, 0.9f, 0.25f, 1f);
    protected static final PathInterpolator LAUNCH = new PathInterpolator(0.7f, 0f, 0.2f, 1f);
    protected static final PathInterpolator SHEEN = new PathInterpolator(0.3f, 0.6f, 0.3f, 1f);

    protected final Activity act;
    protected final Host host;
    protected final FrameLayout root;
    /** Screen pixels per design pixel (1280x720 design). */
    protected final float u;
    protected final Handler handler = new Handler(Looper.getMainLooper());
    protected final UiSounds sounds;
    protected final boolean motion;
    protected final Typeface thin, light, regular, medium;
    /** Adapter positions of the launchable tiles, in Home order. */
    protected final List<Integer> apps = new ArrayList<>();
    protected TextView clockView, ampmView, dateView, greetingView;

    private final FrameLayout launchView;
    private final GradientDrawable launchBg;
    private final TextView launchName;
    private String appsSignature;
    private boolean resumed, launching;

    StyleHome(Activity a, Host h) {
        act = a;
        host = h;
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        u = Math.max(dm.widthPixels, dm.heightPixels) / 1280f;
        motion = h.animations();
        sounds = new UiSounds(soundsOn(a));
        thin = UiFont.get(a, R.string.font_extralight);
        light = UiFont.get(a, R.string.font_light);
        regular = UiFont.get(a, R.string.font_regular);
        medium = UiFont.get(a, R.string.font_medium);
        root = new FrameLayout(a);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        launchView = new FrameLayout(a);
        launchBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.BLACK, Color.BLACK});
        launchView.setBackground(launchBg);
        LinearLayout launchText = new LinearLayout(a);
        launchText.setOrientation(LinearLayout.VERTICAL);
        launchText.setGravity(Gravity.CENTER);
        launchName = text(84, medium, WHITE);
        TextView opening = text(16, regular, 0xB3FFFFFF);
        opening.setLetterSpacing(0.3f);
        opening.setText(R.string.style_opening);
        launchText.addView(launchName);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = px(14);
        launchText.addView(opening, olp);
        launchView.addView(launchText, new FrameLayout.LayoutParams(-1, -1));
        launchView.setVisibility(View.GONE);
    }

    /** Builds the screen and puts it over MainActivity's content. */
    final void attach(ViewGroup content) {
        build();
        root.addView(launchView, new FrameLayout.LayoutParams(-1, -1));
        content.addView(root, new ViewGroup.LayoutParams(-1, -1));
        updateClock();
    }

    protected abstract void build();
    /** The app list changed (or was released): rebuild the tiles from {@link #apps}. */
    protected abstract void bindApps();
    /** A D-pad, OK or Back press (DOWN). True when it did something. */
    protected abstract boolean key(int keyCode);
    /** Home pressed while Home is in front: back to the first app. */
    protected abstract void focusFirst();
    /** Home came back: re-apply the current focus without animation, so its texts are current. */
    protected abstract void refocus();
    /** The network pill's text changed (it is read once a minute and shortly after Home comes back). */
    protected void refreshNetwork(CharSequence label) {}
    protected void startAmbient() {}
    protected void stopAmbient() {}

    // ---- lifecycle, called by MainActivity ----------------------------------------------------------------

    final void onAppsChanged() {
        AppAdapter ad = host.adapter();
        List<Integer> fresh = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i < ad.appCount(); i++) {
            if (!ad.isLaunchableAt(i)) continue;
            fresh.add(i);
            sig.append(ad.keyAt(i)).append('|').append(ad.labelAt(i)).append('\n');
        }
        if (sig.toString().equals(appsSignature)) return;
        appsSignature = sig.toString();
        apps.clear();
        apps.addAll(fresh);
        bindApps();
    }

    final void onResume() {
        resumed = true;
        if (launchView.getVisibility() == View.VISIBLE) hideLaunch();
        handler.removeCallbacks(clockTick);
        clockTick.run();
        onAppsChanged();
        refocus();
        // MainActivity refreshes the network pill after its first frame back; read it once that has happened.
        handler.postDelayed(() -> refreshNetwork(host.networkLabel()), 1500);
        startAmbient();
    }

    final void onPause() {
        resumed = false;
        handler.removeCallbacks(clockTick);
        stopAmbient();
    }

    /** Memory is short and Home is in the background: let go of the artwork; onResume rebuilds it. */
    final void release() {
        appsSignature = null;
        apps.clear();
        bindApps();
    }

    final void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        sounds.release();
    }

    final void onHome() {
        if (!launching) focusFirst();
    }

    /** Takes the D-pad, OK and Back; every other key (volume, Home, Menu) goes on to MainActivity. */
    final boolean dispatchKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        boolean arrow = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!ok && !back && !arrow) return false;
        if (launching || e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if ((ok || back) && e.getRepeatCount() > 0) return true;
        key(ok ? KeyEvent.KEYCODE_DPAD_CENTER : back ? KeyEvent.KEYCODE_BACK : code);
        return true;
    }

    // ---- clock ----------------------------------------------------------------------------------------------

    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            updateClock();
            refreshNetwork(host.networkLabel());
            handler.postDelayed(this, (60 - LocalDateTime.now().getSecond()) * 1000L + 50);
        }
    };

    protected void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        boolean h24 = DateFormat.is24HourFormat(act);
        if (clockView != null) {
            clockView.setText(now.format(DateTimeFormatter.ofPattern(h24 ? "H:mm" : "h:mm", Locale.getDefault())));
        }
        if (ampmView != null) {
            ampmView.setVisibility(h24 ? View.GONE : View.VISIBLE);
            ampmView.setText(now.format(DateTimeFormatter.ofPattern("a", Locale.getDefault())).toUpperCase(Locale.getDefault()));
        }
        if (dateView != null) {
            dateView.setText(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                    .toUpperCase(Locale.getDefault()));
        }
        if (greetingView != null) {
            greetingView.setText(String.valueOf(host.greeting()).toUpperCase(Locale.getDefault()));
        }
    }

    // ---- launching ------------------------------------------------------------------------------------------

    /**
     * Opens apps.get(index). With animations on, a panel in the app's colour grows from the tile to the whole
     * screen while the app starts, so a slow app start reads as part of the motion instead of a frozen Home.
     */
    protected final void launch(int index, View from) {
        if (index < 0 || index >= apps.size()) return;
        final int pos = apps.get(index);
        AppAdapter ad = host.adapter();
        sounds.whoosh();
        recordOpened(ad.packageAt(pos));
        if (!motion || from == null || root.getWidth() == 0) {
            ad.launchAt(pos);
            return;
        }
        launching = true;
        int color = ad.ambientColorAt(pos);
        launchBg.setColors(new int[]{color, darken(color, 0.35f)});
        launchName.setText(ad.labelAt(pos));
        int[] a = new int[2], b = new int[2];
        from.getLocationInWindow(a);
        root.getLocationInWindow(b);
        final float W = root.getWidth(), H = root.getHeight();
        final float sx = Math.max(0.05f, from.getWidth() * from.getScaleX() / W);
        final float sy = Math.max(0.05f, from.getHeight() * from.getScaleY() / H);
        final float tx = a[0] - b[0], ty = a[1] - b[1];
        final float r0 = 16 * u;
        launchView.setPivotX(0);
        launchView.setPivotY(0);
        launchView.setAlpha(1f);
        launchView.setVisibility(View.VISIBLE);
        ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f).setDuration(480);
        grow.setInterpolator(LAUNCH);
        grow.addUpdateListener(va -> {
            float p = (float) va.getAnimatedValue();
            float scx = sx + (1 - sx) * p, scy = sy + (1 - sy) * p;
            launchView.setScaleX(scx);
            launchView.setScaleY(scy);
            launchView.setTranslationX(tx * (1 - p));
            launchView.setTranslationY(ty * (1 - p));
            launchBg.setCornerRadius(r0 * (1 - p) / Math.min(scx, scy));
        });
        grow.start();
        // The app's own starting window covers the screen as soon as it is started, so the panel gets most of its
        // growth first: otherwise it is never seen.
        handler.postDelayed(() -> ad.launchAt(pos), 400);
        // The app did not come up (it failed, or a toast said so): give Home back.
        handler.postDelayed(() -> { if (resumed) hideLaunch(); }, 4000);
    }

    private void hideLaunch() {
        launching = false;
        launchView.animate().cancel();
        launchView.animate().alpha(0f).setDuration(motion ? 380 : 0)
                .withEndAction(() -> launchView.setVisibility(View.GONE)).start();
    }

    private void recordOpened(String pkg) {
        if (pkg == null) return;
        act.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit()
                .putLong("opened:" + pkg, System.currentTimeMillis()).apply();
    }

    /** "Opened 2 hours ago", or a hint for an app never opened from a style. */
    protected final String openedLine(int index) {
        if (index < 0 || index >= apps.size()) return "";
        String pkg = host.adapter().packageAt(apps.get(index));
        long t = act.getSharedPreferences(STATE, Context.MODE_PRIVATE).getLong("opened:" + pkg, 0);
        if (t <= 0) return act.getString(R.string.style_ok_to_open);
        long now = System.currentTimeMillis();
        if (now - t < DateUtils.MINUTE_IN_MILLIS) return act.getString(R.string.style_opened_just_now);
        return act.getString(R.string.style_opened, DateUtils.getRelativeTimeSpanString(t, now,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
    }

    protected final String labelOf(int index) {
        return index >= 0 && index < apps.size() ? host.adapter().labelAt(apps.get(index)) : "";
    }

    protected final int colorOf(int index) {
        return index >= 0 && index < apps.size() ? host.adapter().ambientColorAt(apps.get(index)) : 0xFF444444;
    }

    // ---- building blocks ------------------------------------------------------------------------------------

    protected final int px(float design) {
        return Math.round(design * u);
    }

    protected final TextView text(float sizeDesign, Typeface tf, int color) {
        TextView t = new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizeDesign * u);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine();
        return t;
    }

    /** Adds v to parent at a design position; w or h below 0 wraps the content. */
    protected final <V extends View> V add(ViewGroup parent, V v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? -2 : px(w), h < 0 ? -2 : px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        lp.gravity = Gravity.TOP | Gravity.START;
        parent.addView(v, lp);
        return v;
    }

    /** A view centred horizontally across the screen at a design y. */
    protected final <V extends TextView> V addCentered(ViewGroup parent, V v, float y) {
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        return add(parent, v, 0, y, 1280, -1);
    }

    protected static GradientDrawable rounded(int fill, float radiusPx, int strokePx, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    protected static ViewOutlineProvider roundOutline(final float radiusPx) {
        return new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        };
    }

    protected static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    protected static int withAlpha(int c, int alpha) {
        return (c & 0x00FFFFFF) | (alpha << 24);
    }

    /** The shadow under a raised tile in the app's own colour, a glow (Android 9 and later; black before). */
    protected static void glow(View v, int color) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            v.setOutlineSpotShadowColor(color);
            v.setOutlineAmbientShadowColor(color);
        }
    }

    /** A copy that shares the bitmap but has its own bounds, so two views can draw one app's artwork. */
    protected final Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(act.getResources()).mutate() : d;
    }

    /** One app's tile: its banner, or its icon and name on a plate in its colour; a sheen can sweep across it. */
    static final class Tile extends FrameLayout {
        int index;
        int color;
        View sheen;
        View dim;
        Tile(Context c) { super(c); }

        void sweep(boolean motion) {
            if (!motion || sheen == null || getWidth() == 0) return;
            sheen.animate().cancel();
            sheen.setTranslationX(-getWidth() * 0.9f);
            sheen.animate().translationX(getWidth() * 1.3f).setDuration(900).setInterpolator(SHEEN).start();
        }
    }

    protected final Tile tile(int index, float wDesign, float hDesign, float radiusDesign) {
        Tile t = new Tile(act);
        t.index = index;
        int pos = apps.get(index);
        AppAdapter ad = host.adapter();
        t.color = ad.ambientColorAt(pos);
        int w = px(wDesign), h = px(hDesign);
        t.setOutlineProvider(roundOutline(radiusDesign * u));
        t.setClipToOutline(true);
        Drawable banner = ad.bannerAt(pos);
        if (banner != null) {
            ImageView iv = new ImageView(act);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(copy(banner));
            t.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            GradientDrawable plate = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{darken(t.color, 0.55f), darken(t.color, 0.18f)});
            t.setBackground(plate);
            Drawable icon = ad.iconAt(pos);
            int iconSize = Math.round(h * 0.46f);
            if (icon != null) {
                ImageView iv = new ImageView(act);
                iv.setImageDrawable(copy(icon));
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize);
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                lp.topMargin = Math.round(h * 0.13f);
                t.addView(iv, lp);
            }
            TextView name = new TextView(act);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, h * 0.13f);
            name.setTypeface(medium);
            name.setTextColor(0xE6FFFFFF);
            name.setSingleLine();
            name.setIncludeFontPadding(false);
            name.setText(ad.labelAt(pos));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
            lp.bottomMargin = Math.round(h * 0.1f);
            t.addView(name, lp);
        }
        t.dim = new View(act);
        t.dim.setBackgroundColor(Color.BLACK);
        t.dim.setAlpha(0f);
        t.addView(t.dim, new FrameLayout.LayoutParams(-1, -1));
        t.sheen = new View(act);
        t.sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}));
        t.sheen.setRotation(18f);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Math.round(w * 0.42f), h * 2);
        slp.topMargin = -h / 2;
        t.addView(t.sheen, slp);
        t.sheen.setTranslationX(-w * 2f);
        return t;
    }

    /**
     * The app's artwork shrunk to a few pixels and scaled back up: GPU bilinear filtering turns that into a soft,
     * blur-like wash of the app's own colours, with no blur pass (none exists before Android 12).
     */
    protected final Drawable wash(int index) {
        if (index < 0 || index >= apps.size()) return null;
        AppAdapter ad = host.adapter();
        Drawable art = copy(ad.artworkAt(apps.get(index)));
        if (art == null) return null;
        Bitmap b = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.BLACK);
        boolean banner = ad.bannerAt(apps.get(index)) != null;
        if (banner) art.setBounds(0, 0, 160, 90);
        else art.setBounds(50, 15, 110, 75);
        art.draw(c);
        Bitmap s = b;
        for (int w = 80, h = 45; w >= 10; w /= 2, h = Math.max(3, h / 2)) {
            Bitmap next = Bitmap.createScaledBitmap(s, w, h, true);
            if (s != b) s.recycle();
            s = next;
        }
        Bitmap up = Bitmap.createScaledBitmap(s, 128, 72, true);
        s.recycle();
        b.recycle();
        BitmapDrawable d = new BitmapDrawable(act.getResources(), up);
        d.setFilterBitmap(true);
        return d;
    }

    /** A soft white disc fading to transparent, tinted per use: the light behind the aurora and orbit styles. */
    protected static Bitmap softDisc() {
        int n = 64;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        float c = (n - 1) / 2f;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;
                float a = Math.max(0f, Math.min(1f, (float) Math.pow(Math.max(0, 1 - d), 1.6)));
                b.setPixel(x, y, Color.argb(Math.round(a * 255), 255, 255, 255));
            }
        }
        return b;
    }

    // ---- top bar pills (aurora, orbit) ----------------------------------------------------------------------

    protected final TextView pill(int icon, CharSequence label) {
        TextView p = text(17, medium, WHITE);
        p.setGravity(Gravity.CENTER_VERTICAL);
        boolean iconOnly = label == null;
        Drawable d = act.getDrawable(icon).mutate();
        int s = px(20);
        d.setBounds(0, 0, s, s);
        p.setCompoundDrawablesRelative(d, null, null, null);
        if (iconOnly) {
            p.setPadding((px(50) - s) / 2, 0, 0, 0);
        } else {
            p.setText(label);
            p.setCompoundDrawablePadding(px(10));
            p.setPadding(px(22), 0, px(24), 0);
        }
        p.setBackground(rounded(0x14FFFFFF, 25 * u, Math.max(1, px(1)), 0x22FFFFFF));
        p.setTag(iconOnly);
        return p;
    }

    protected final void focusPill(TextView p, boolean focused) {
        int fg = focused ? Color.BLACK : WHITE;
        p.setTextColor(fg);
        for (Drawable d : p.getCompoundDrawablesRelative()) if (d != null) d.setTint(fg);
        p.setBackground(focused ? rounded(WHITE, 25 * u, 0, 0)
                : rounded(0x14FFFFFF, 25 * u, Math.max(1, px(1)), 0x22FFFFFF));
        float s = focused ? 1.08f : 1f;
        if (motion) p.animate().scaleX(s).scaleY(s).setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
        else { p.setScaleX(s); p.setScaleY(s); }
    }

    /** The edge of a row or the top: a small push in that direction and back, with a soft thunk. */
    protected final void bump(View v, int dx, int dy) {
        sounds.thunk();
        if (v == null || !motion) return;
        float bx = v.getTranslationX(), by = v.getTranslationY();
        v.animate().cancel();
        v.animate().translationX(bx + dx * 9 * u).translationY(by + dy * 7 * u).setDuration(90)
                .setInterpolator(SMOOTH)
                .withEndAction(() -> v.animate().translationX(bx).translationY(by).setDuration(220)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start())
                .start();
    }

    /** Fades a text out, swaps it and brings it back from a few pixels away (at once when not animating). */
    protected final void swapText(TextView t, CharSequence text, float fromX, float fromY, boolean animate) {
        if (t == null || String.valueOf(text).contentEquals(t.getText())) return;
        if (!motion || !animate) {
            t.animate().cancel();
            t.setText(text);
            t.setAlpha(1f);
            t.setTranslationX(0);
            t.setTranslationY(0);
            return;
        }
        t.animate().cancel();
        t.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            t.setText(text);
            t.setTranslationX(fromX * u);
            t.setTranslationY(fromY * u);
            t.animate().alpha(1f).translationX(0).translationY(0).setDuration(380).setInterpolator(SMOOTH).start();
        }).start();
    }
}
