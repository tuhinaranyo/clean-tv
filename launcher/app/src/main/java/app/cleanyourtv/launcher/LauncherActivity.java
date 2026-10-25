package app.cleanyourtv.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class LauncherActivity extends Activity {
    private Board board;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Th.refresh(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Th.c(0xFF0F0F0F)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        board = new Board(this);
        setContentView(board);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Th.refresh(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Th.c(0xFF0F0F0F)));
        board.reload();
        board.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        board.goHome();
    }

    @Override
    public void onBackPressed() {
        board.back();
    }

    // ------------------------------------------------------------------------------------------

    static class Tile {
        String pkg, label, sub;
        Intent intent;
        Bitmap bmp;
        BitmapShader shader;
        boolean ftp;
        float hue;
        float scale = 1f;
        final RectF r = new RectF();
    }

    static class Entry {
        String label;
        Intent intent;
    }

    static class Spring {
        float x, v, t;
        Spring(float x) { this.x = x; this.t = x; this.v = 0; }
        void snap(float val) { x = t = val; v = 0; }
        boolean step(float dt, float k, float c) {
            float rem = dt;
            while (rem > 0) {
                float h = Math.min(rem, 1f / 120f);
                v += (k * (t - x) - c * v) * h;
                x += v * h;
                rem -= h;
            }
            if (Math.abs(t - x) < 0.4f && Math.abs(v) < 3f) { x = t; v = 0; return false; }
            return true;
        }
    }

    static final int RAIL = 0, CONTENT = 1;
    static final float CW = 480, CH = 270, GAP = 32, X0 = 156, XEXP = 534, RAILW = 78;
    static final float[] RAILY = {270, 348, 426, 504, 983};

    class Board extends View {
        final float U;
        final PackageManager pm;
        final Handler ui = new Handler();
        final List<List<Tile>> rows = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final List<Entry> inputs = new ArrayList<>();
        final Set<String> hidden = new HashSet<>();
        final String[] dockPkgs;

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF tmp = new RectF(), tmp2 = new RectF();
        final Typeface reg, med;

        final Spring off = new Spring(0), cx = new Spring(0), ry = new Spring(0);
        final List<Spring> rs = new ArrayList<>();
        float ddAlpha;
        long last;
        boolean placed;
        long clockMin = -1;
        String clockStr = "", dateStr = "";
        final Path homePath = new Path();

        int sec = CONTENT, row = 0, col = 0, rail = 0;
        boolean ddOpen;
        int ddIdx;
        String ssid = "Wi-Fi";
        float avgSat;

        Board(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setDefaultFocusHighlightEnabled(false);
            pm = c.getPackageManager();
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
            dockPkgs = c.getResources().getStringArray(R.array.dock_apps);
            Collections.addAll(hidden, c.getResources().getStringArray(R.array.hidden_apps));
            hidden.add(c.getPackageName());
            reg = Typeface.create("sans-serif", Typeface.NORMAL);
            med = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            cx.snap(X0 * U);
            tick();
        }

        void tick() {
            if (Th.refresh(getContext())) {
                LauncherActivity.this.getWindow().setBackgroundDrawable(new ColorDrawable(Th.c(0xFF0F0F0F)));
                reload();
            }
            invalidate();
            ui.postDelayed(new Runnable() { public void run() { tick(); } }, 20000);
        }

        // ---------------- data ----------------

        final java.util.HashMap<String, Tile> cache = new java.util.HashMap<>();

        Tile tile(String key) {
            Tile t = cache.get(key);
            if (t == null) { t = new Tile(); cache.put(key, t); }
            return t;
        }

        void reload() {
            readWifi();
            readInputs();
            final List<Tile> all = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                    String pkg = ri.activityInfo.packageName;
                    if (hidden.contains(pkg) || !seen.add(pkg)) continue;
                    Tile t = tile(pkg);
                    t.pkg = pkg;
                    t.label = String.valueOf(ri.loadLabel(pm));
                    t.intent = new Intent(Intent.ACTION_MAIN).setClassName(pkg, ri.activityInfo.name)
                            .addCategory(cat).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    all.add(t);
                }
            }
            Collections.sort(all, new Comparator<Tile>() {
                public int compare(Tile x, Tile y) { return x.label.compareToIgnoreCase(y.label); }
            });
            List<Tile> apps = new ArrayList<>();
            for (String pkg : dockPkgs) {
                for (Tile t : all) if (t.pkg.equals(pkg)) { apps.add(t); break; }
            }
            for (Tile t : all) if (!apps.contains(t)) apps.add(t);

            List<Servers.S> servers = Servers.load(Board.this.getContext());
            List<Tile> ftps = new ArrayList<>();
            for (int i = 0; i < servers.size(); i++) {
                if (!servers.get(i).on) continue;
                Tile t = tile("ftp:" + i);
                t.pkg = "ftp:" + i;
                t.label = servers.get(i).name;
                try { t.sub = new java.net.URL(servers.get(i).url).getHost(); } catch (Throwable ignored) { }
                t.ftp = true;
                t.hue = new float[]{150f, 188f, 262f, 32f, 330f, 52f}[i % 6];
                t.intent = new Intent(Board.this.getContext(), FtpActivity.class).putExtra("title", servers.get(i).name)
                        .putExtra("url", servers.get(i).url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ftps.add(t);
            }
            rows.clear();
            names.clear();
            if (!apps.isEmpty()) { rows.add(apps); names.add("Apps"); }
            if (!ftps.isEmpty()) { rows.add(ftps); names.add("Servers"); }
            while (rs.size() < rows.size()) rs.add(new Spring(0));
            clampFocus();
            final List<Tile> work = new ArrayList<>();
            for (List<Tile> l : rows) work.addAll(l);
            new Thread(new Runnable() { public void run() {
                for (Tile t : work) { if (t.bmp == null) render(t); }
                postInvalidate();
            } }).start();
            retarget(true);
        }

        void readWifi() {
            String s = "Wi-Fi";
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null && wi.getSSID() != null && !wi.getSSID().contains("unknown")) s = wi.getSSID().replace("\"", "");
            } catch (Throwable ignored) { }
            ssid = s;
        }

        void readInputs() {
            inputs.clear();
            try {
                TvInputManager tm = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
                for (TvInputInfo ti : tm.getTvInputList()) {
                    if (!ti.isPassthroughInput()) continue;
                    CharSequence cl = ti.loadCustomLabel(Board.this.getContext());
                    CharSequence l = cl != null && cl.length() > 0 ? cl : ti.loadLabel(Board.this.getContext());
                    Entry e = new Entry();
                    e.label = String.valueOf(l);
                    e.intent = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(ti.getId()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    inputs.add(e);
                }
            } catch (Throwable ignored) { }
            if (inputs.isEmpty()) {
                Entry e = new Entry();
                e.label = "No inputs found";
                inputs.add(e);
            }
        }

        void render(Tile t) {
            final float SS = 1.5f;
            int w = Math.round(CW * U * SS), h = Math.round(CH * U * SS);
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
            int top, bot;
            Drawable bn = null;
            if (!t.ftp) {
                try {
                    android.content.pm.ActivityInfo ai = pm.getActivityInfo(t.intent.getComponent(), 0);
                    int id = ai.banner != 0 ? ai.banner : ai.applicationInfo.banner;
                    if (id != 0) {
                        android.content.res.Resources r = pm.getResourcesForApplication(ai.applicationInfo);
                        bn = r.getDrawableForDensity(id, 640, null);
                    }
                } catch (Throwable ignored) { }
                if (bn == null) { try { bn = pm.getActivityBanner(t.intent); } catch (Throwable ignored) { } }
                if (bn == null) { try { bn = pm.getApplicationBanner(t.pkg); } catch (Throwable ignored) { } }
            }
            if ("com.google.android.youtube.tv".equals(t.pkg) || "com.google.android.youtube.tvmusic".equals(t.pkg)) {
                c.drawColor(0xFFFFFFFF);
                float cx = w / 2f, cy = h / 2f;
                q.setColor(0xFFFF0000);
                Path tr = new Path();
                if (t.pkg.endsWith("tvmusic")) {
                    float r = h * 0.30f;
                    c.drawCircle(cx, cy, r, q);
                    q.setColor(Color.WHITE);
                    q.setStyle(Paint.Style.STROKE);
                    q.setStrokeWidth(r * 0.09f);
                    c.drawCircle(cx, cy, r * 0.80f, q);
                    q.setStyle(Paint.Style.FILL);
                    tr.moveTo(cx - r * 0.26f, cy - r * 0.40f); tr.lineTo(cx + r * 0.46f, cy); tr.lineTo(cx - r * 0.26f, cy + r * 0.40f);
                } else {
                    float hw = h * 0.40f, hh = hw * 0.70f;
                    c.drawRoundRect(new RectF(cx - hw, cy - hh, cx + hw, cy + hh), hh * 0.42f, hh * 0.42f, q);
                    q.setColor(Color.WHITE);
                    tr.moveTo(cx - hh * 0.40f, cy - hh * 0.56f); tr.lineTo(cx + hh * 0.62f, cy); tr.lineTo(cx - hh * 0.40f, cy + hh * 0.56f);
                }
                tr.close();
                c.drawPath(tr, q);
            } else if (bn != null) {
                bn.setBounds(0, 0, w, h);
                bn.draw(c);
            } else if (t.ftp) {
                float[] hs = {t.hue, 0.62f, 0.95f};
                top = Color.HSVToColor(hs);
                hs[1] = 0.85f; hs[2] = 0.32f;
                bot = Color.HSVToColor(hs);
                q.setShader(new LinearGradient(0, 0, w * 0.4f, h, top, bot, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, q);
                q.setShader(null);
                float cx = w / 2f, cy = h * 0.5f, r = h * 0.25f;
                q.setColor(0x33FFFFFF);
                c.drawCircle(cx, cy, r, q);
                q.setColor(Color.WHITE);
                Path tri = new Path();
                tri.moveTo(cx - r * 0.3f, cy - r * 0.48f);
                tri.lineTo(cx + r * 0.55f, cy);
                tri.lineTo(cx - r * 0.3f, cy + r * 0.48f);
                tri.close();
                c.drawPath(tri, q);
            } else {
                Drawable ic = null;
                try { ic = pm.getActivityIcon(t.intent); } catch (Throwable ignored) { }
                int dom = 0;
                if (ic != null) {
                    Bitmap sm = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
                    Canvas sc = new Canvas(sm);
                    ic.setBounds(0, 0, 48, 48);
                    ic.draw(sc);
                    dom = sample(sm);
                    sm.recycle();
                }
                float[] hsv = new float[3];
                Color.colorToHSV(dom, hsv);
                boolean neutral = ic == null || avgSat < 0.22f;
                if (neutral) { top = 0xFF4A4A54; bot = 0xFF17171C; }
                else {
                    float hh = hsv[0];
                    top = Color.HSVToColor(new float[]{hh, Math.min(1f, hsv[1] * 0.8f), 0.92f});
                    bot = Color.HSVToColor(new float[]{hh, Math.min(1f, hsv[1]), 0.34f});
                }
                q.setShader(new LinearGradient(0, 0, w * 0.35f, h, top, bot, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, q);
                q.setShader(null);
                if (ic != null) {
                    int s = Math.round(h * 0.54f);
                    int x0 = (w - s) / 2, y0 = (h - s) / 2;
                    ic.setBounds(x0, y0, x0 + s, y0 + s);
                    ic.draw(c);
                }
            }
            t.shader = new BitmapShader(bm, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            android.graphics.Matrix sm0 = new android.graphics.Matrix();
            sm0.setScale(1f / SS, 1f / SS);
            t.shader.setLocalMatrix(sm0);
            t.bmp = bm;
        }

        int sample(Bitmap bm) {
            float r = 0, g = 0, b = 0, wsum = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < bm.getHeight(); y += 2)
                for (int x = 0; x < bm.getWidth(); x += 2) {
                    int px = bm.getPixel(x, y);
                    Color.colorToHSV(px, hsv);
                    float w = hsv[1] * hsv[2] + 0.02f;
                    r += Color.red(px) * w; g += Color.green(px) * w; b += Color.blue(px) * w; wsum += w;
                }
            int c = Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (b / wsum));
            Color.colorToHSV(c, hsv);
            avgSat = hsv[1];
            hsv[1] = Math.min(1f, Math.max(hsv[1], 0.5f));
            hsv[2] = Math.max(hsv[2], 0.85f);
            return Color.HSVToColor(hsv);
        }

        // ---------------- layout / focus ----------------

        float rowY0(int r) { return 170 + r * 470; }

        void clampFocus() {
            if (rows.isEmpty()) { sec = RAIL; return; }
            row = Math.max(0, Math.min(row, rows.size() - 1));
            col = Math.max(0, Math.min(col, rows.get(row).size() - 1));
        }

        void retarget(boolean snap) {
            cx.t = ((sec == RAIL || ddOpen) ? XEXP : X0) * U;
            off.t = (sec == CONTENT || ddOpen) && row > 0 ? (rowY0(row) - 170) * U : (sec == RAIL ? off.t : 0);
            if (sec == CONTENT && row == 0) off.t = 0;
            ry.t = RAILY[rail] * U;
            if (!rows.isEmpty() && row < rs.size()) {
                float left = col * (CW + GAP), right = left + CW, view = 1920 - X0 - 60, s = rs.get(row).t;
                if (right - s > view) s = right - view;
                if (left - s < 0) s = left;
                rs.get(row).t = Math.max(0, s * U);
            }
            if (snap || !placed) {
                cx.snap(cx.t); off.snap(off.t); ry.snap(ry.t);
                for (Spring s : rs) s.snap(s.t);
                placed = true;
            }
            invalidate();
        }

        // ---------------- input ----------------

        void goHome() {
            ddOpen = false;
            sec = rows.isEmpty() ? RAIL : CONTENT;
            row = 0; col = 0; rail = 0;
            for (Spring s : rs) s.t = 0;
            retarget(false);
        }

        void back() {
            if (ddOpen) { ddOpen = false; retarget(false); return; }
            if (sec == RAIL && !rows.isEmpty()) { sec = CONTENT; retarget(false); return; }
            if (row != 0 || col != 0) { row = 0; col = 0; for (Spring s : rs) s.t = 0; retarget(false); }
        }

        void launch(Intent i) {
            try { startActivity(i); } catch (Throwable ignored) { }
        }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            boolean center = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (ddOpen) {
                if (k == KeyEvent.KEYCODE_DPAD_UP) ddIdx = Math.max(0, ddIdx - 1);
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) ddIdx = Math.min(inputs.size() - 1, ddIdx + 1);
                else if (center && e.getRepeatCount() == 0) {
                    Intent i = inputs.get(ddIdx).intent;
                    ddOpen = false;
                    if (i != null) launch(i);
                } else if (k != KeyEvent.KEYCODE_DPAD_LEFT && k != KeyEvent.KEYCODE_DPAD_RIGHT) return super.onKeyDown(k, e);
                else if (k == KeyEvent.KEYCODE_DPAD_LEFT) ddOpen = false;
                retarget(false);
                return true;
            }
            if (sec == RAIL) {
                switch (k) {
                    case KeyEvent.KEYCODE_DPAD_UP: if (rail > 0) rail--; break;
                    case KeyEvent.KEYCODE_DPAD_DOWN: if (rail < RAILY.length - 1) rail++; break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: if (!rows.isEmpty()) sec = CONTENT; break;
                    case KeyEvent.KEYCODE_DPAD_LEFT: break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_NUMPAD_ENTER:
                        if (e.getRepeatCount() > 0) return true;
                        if (rail == 0) { if (!rows.isEmpty()) sec = CONTENT; }
                        else if (rail == 1) { readInputs(); ddOpen = true; ddIdx = 0; }
                        else if (rail == 2) launch(PageActivity.page(Board.this.getContext(), "wifi"));
                        else if (rail == 3) launch(new Intent(Board.this.getContext(), ServersActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        else launch(new Intent(Board.this.getContext(), SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        break;
                    default: return super.onKeyDown(k, e);
                }
                retarget(false);
                return true;
            }
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (col > 0) col--; else { sec = RAIL; rail = 0; }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (col < rows.get(row).size() - 1) col++;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (row > 0) { row--; col = Math.min(col, rows.get(row).size() - 1); }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (row < rows.size() - 1) { row++; col = Math.min(col, rows.get(row).size() - 1); }
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() > 0) return true;
                    launch(rows.get(row).get(col).intent);
                    break;
                default:
                    return super.onKeyDown(k, e);
            }
            retarget(false);
            return true;
        }

        // ---------------- drawing ----------------

        void spring(float dt) {
            boolean run = false;
            run |= cx.step(dt, 380, 32);
            run |= off.step(dt, 300, 34);
            run |= ry.step(dt, 520, 36);
            for (Spring s : rs) run |= s.step(dt, 300, 34);
            float k = Math.min(1f, dt * 12f);
            ddAlpha += ((ddOpen ? 1f : 0f) - ddAlpha) * Math.min(1f, dt * 14f);
            if (Math.abs((ddOpen ? 1f : 0f) - ddAlpha) > 0.01f) run = true;
            Tile cur = (sec == CONTENT && !ddOpen && !rows.isEmpty()) ? rows.get(row).get(col) : null;
            for (List<Tile> l : rows) for (Tile t : l) {
                float tgt = t == cur ? 1.07f : 1f;
                t.scale += (tgt - t.scale) * k;
                if (Math.abs(tgt - t.scale) > 0.002f) run = true; else t.scale = tgt;
            }
            if (run) postInvalidateOnAnimation();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            retarget(true);
        }

        String fit(String s, float maxPx) {
            if (p.measureText(s) <= maxPx) return s;
            while (s.length() > 3 && p.measureText(s + "…") > maxPx) s = s.substring(0, s.length() - 1);
            return s + "…";
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            c.drawColor(Th.c(0xFF0F0F0F));
            spring(dt);
            p.setShader(null);
            if (rows.isEmpty()) {
                p.setColor(Th.c(0x99FFFFFF)); p.setTypeface(reg); p.setTextSize(40 * U); p.setTextAlign(Paint.Align.LEFT);
                c.drawText("No apps found", cx.x, 400 * U, p);
            }
            drawContent(c);
            drawClock(c);
            drawRail(c);
            drawDropdown(c);
        }

        void drawClock(Canvas c) {
            long min = System.currentTimeMillis() / 60000;
            if (min != clockMin) {
                clockMin = min;
                boolean h24 = DateFormat.is24HourFormat(getContext());
                clockStr = DateFormat.format(h24 ? "HH:mm" : "h:mm", System.currentTimeMillis()).toString();
                dateStr = new java.text.SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date());
            }
            String time = clockStr;
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(med);
            p.setColor(Th.W());
            p.setTextSize(56 * U);
            c.drawText(time, 1860 * U, 112 * U, p);
            p.setTypeface(reg);
            p.setColor(Th.c(0xFFAAAAAA));
            p.setTextSize(26 * U);
            c.drawText(dateStr, 1860 * U, 150 * U, p);
        }

        void drawContent(Canvas c) {
            c.save();
            c.clipRect(Math.max(0f, cx.x - 44 * U), 100 * U, getWidth(), getHeight());
            for (int r = 0; r < rows.size(); r++) {
                float y0 = (rowY0(r)) * U - off.x;
                if (y0 > getHeight() + 40 * U || y0 + 440 * U < 168 * U) continue;
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(med);
                p.setColor(Th.c(0xCCFFFFFF));
                p.setTextSize(34 * U);
                c.drawText(names.get(r), cx.x, y0, p);
                List<Tile> l = rows.get(r);
                float sx = rs.get(r).x;
                for (int i = 0; i < l.size(); i++) {
                    Tile t = l.get(i);
                    float x = cx.x + i * (CW + GAP) * U - sx, y = y0 + 46 * U;
                    t.r.set(x, y, x + CW * U, y + CH * U);
                    if (t.r.right < cx.x - 40 * U || t.r.left > getWidth()) continue;
                    drawTile(c, t);
                    drawTitle(c, t, sec == CONTENT && !ddOpen && r == row && i == col);
                }
            }
            c.restore();
        }

        void drawTile(Canvas c, Tile t) {
            float w = t.r.width(), h = t.r.height();
            float lift = (t.scale - 1f) / 0.07f;
            c.save();
            c.translate(t.r.centerX(), t.r.centerY());
            c.scale(t.scale, t.scale);
            c.translate(-w / 2, -h / 2);
            tmp.set(0, 0, w, h);
            if (t.shader != null) {
                p.setFilterBitmap(true);
                p.setColor(Th.K());
                p.setAlpha(255);
                p.setShader(t.shader);
                c.drawRoundRect(tmp, 16 * U, 16 * U, p);
                p.setShader(null);
                if (lift < 0.98f) {
                    p.setColor(Color.argb((int) (60 * (1f - Math.max(0f, lift))), 0, 0, 0));
                    c.drawRoundRect(tmp, 16 * U, 16 * U, p);
                }
            } else {
                p.setColor(Th.c(0xFF272727));
                c.drawRoundRect(tmp, 16 * U, 16 * U, p);
            }
            if (lift > 0.02f) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5 * U);
                p.setColor(Color.argb((int) (255 * Math.min(1f, lift)), Th.light ? 15 : 255, Th.light ? 15 : 255, Th.light ? 15 : 255));
                tmp.inset(-2.5f * U, -2.5f * U);
                c.drawRoundRect(tmp, 19 * U, 19 * U, p);
                p.setStyle(Paint.Style.FILL);
            }
            c.restore();
        }

        void drawTitle(Canvas c, Tile t, boolean f) {
            float y = t.r.bottom + (t.scale - 1f) * t.r.height() / 2 + 52 * U;
            float x = t.r.left + (t.scale - 1f) * -t.r.width() / 2 * 0 + 4 * U;
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(med);
            p.setTextSize(34 * U);
            p.setColor(f ? Th.W() : Th.c(0xFFAAAAAA));
            c.drawText(fit(t.label, (CW - 8) * U), x, y, p);
            if (t.sub != null) {
                p.setTypeface(reg);
                p.setTextSize(26 * U);
                p.setColor(Th.c(0xFF909090));
                c.drawText(fit(t.sub, (CW - 8) * U), x, y + 38 * U, p);
            }
        }

        void drawRail(Canvas c) {
            float e = Math.max(0f, Math.min(1f, (cx.x / U - X0) / (XEXP - X0)));
            String[] labels = {"Home", "Inputs", ssid, "Servers", "Settings"};
            if (sec == RAIL || ddOpen) {
                float pw = (RAILW + (444 - RAILW) * e) * U;
                tmp.set(33 * U, ry.x - 39 * U, 33 * U + pw, ry.x + 39 * U);
                p.setColor(Th.W());
                c.drawRoundRect(tmp, 18 * U, 18 * U, p);
            } else {
                tmp.set(39 * U, RAILY[0] * U - 39 * U, 117 * U, RAILY[0] * U + 39 * U);
                p.setColor(Th.c(0xFF272727));
                c.drawRoundRect(tmp, 18 * U, 18 * U, p);
            }
            if (e > 0.02f) {
                p.setColor(Color.argb((int) (90 * e), 255, 255, 255));
                c.drawRect(33 * U, 565 * U, 477 * U, 567 * U, p);
            }
            for (int i = 0; i < RAILY.length; i++) {
                boolean f = (sec == RAIL || ddOpen) && i == rail;
                int col = f ? Th.c(0xFF0F0F0F) : Th.W();
                drawIcon(c, i, 78 * U, RAILY[i] * U, col);
                if (e > 0.05f) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTextAlign(Paint.Align.LEFT);
                    p.setTypeface(med);
                    p.setTextSize(34 * U);
                    p.setColor(Color.argb((int) (255 * Math.min(1f, e * 1.4f)), Color.red(col), Color.green(col), Color.blue(col)));
                    c.drawText(fit(labels[i], 290 * U), 123 * U, RAILY[i] * U + 12 * U, p);
                }
            }
        }

        void drawIcon(Canvas c, int i, float x, float y, int col) {
            float u = U;
            p.setColor(col);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.6f * u);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            switch (i) {
                case 0: {
                    p.setStyle(Paint.Style.FILL);
                    Path h = homePath;
                    h.reset();
                    h.moveTo(x, y - 21 * u); h.lineTo(x + 23 * u, y - 1 * u); h.lineTo(x + 16 * u, y - 1 * u);
                    h.lineTo(x + 16 * u, y + 18 * u); h.lineTo(x + 5 * u, y + 18 * u); h.lineTo(x + 5 * u, y + 6 * u);
                    h.lineTo(x - 5 * u, y + 6 * u); h.lineTo(x - 5 * u, y + 18 * u); h.lineTo(x - 16 * u, y + 18 * u);
                    h.lineTo(x - 16 * u, y - 1 * u); h.lineTo(x - 23 * u, y - 1 * u); h.close();
                    c.drawPath(h, p);
                    break;
                }
                case 1:
                    tmp2.set(x - 21 * u, y - 15 * u, x + 21 * u, y + 15 * u);
                    c.drawRoundRect(tmp2, 5 * u, 5 * u, p);
                    c.drawLine(x - 10 * u, y, x + 8 * u, y, p);
                    c.drawLine(x + 1 * u, y - 7 * u, x + 8 * u, y, p);
                    c.drawLine(x + 1 * u, y + 7 * u, x + 8 * u, y, p);
                    break;
                case 2:
                    for (int r = 1; r <= 3; r++) {
                        float rr = r * 10.5f * u, cy = y + 14 * u;
                        tmp2.set(x - rr, cy - rr, x + rr, cy + rr);
                        c.drawArc(tmp2, 225, 90, false, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(x, y + 14 * u, 3.4f * u, p);
                    break;
                case 3:
                    tmp2.set(x - 20 * u, y - 20 * u, x + 20 * u, y - 3 * u);
                    c.drawRoundRect(tmp2, 4 * u, 4 * u, p);
                    tmp2.set(x - 20 * u, y + 3 * u, x + 20 * u, y + 20 * u);
                    c.drawRoundRect(tmp2, 4 * u, 4 * u, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(x + 11 * u, y - 11.5f * u, 2.6f * u, p);
                    c.drawCircle(x + 11 * u, y + 11.5f * u, 2.6f * u, p);
                    break;
                default:
                    c.drawCircle(x, y, 13 * u, p);
                    c.drawCircle(x, y, 5 * u, p);
                    p.setStrokeWidth(6 * u);
                    p.setStrokeCap(Paint.Cap.BUTT);
                    for (int k = 0; k < 8; k++) {
                        double an = Math.toRadians(k * 45);
                        c.drawLine(x + (float) Math.cos(an) * 15 * u, y + (float) Math.sin(an) * 15 * u,
                                x + (float) Math.cos(an) * 22 * u, y + (float) Math.sin(an) * 22 * u, p);
                    }
            }
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
        }

        void drawDropdown(Canvas c) {
            if (ddAlpha < 0.02f) return;
            float w = 460 * U, x = (XEXP + 30) * U, y = RAILY[1] * U - 39 * U;
            float h = 24 * U + inputs.size() * 76 * U;
            tmp.set(x, y, x + w, y + h);
            p.setColor(Color.argb((int) (255 * ddAlpha), 33, 33, 33));
            c.drawRoundRect(tmp, 24 * U, 24 * U, p);
            p.setTypeface(med);
            p.setTextSize(34 * U);
            p.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < inputs.size(); i++) {
                float ry0 = y + 12 * U + i * 76 * U;
                boolean f = i == ddIdx;
                if (f) {
                    tmp2.set(x + 12 * U, ry0, x + w - 12 * U, ry0 + 70 * U);
                    p.setColor(Color.argb((int) (255 * ddAlpha), 255, 255, 255));
                    c.drawRoundRect(tmp2, 16 * U, 16 * U, p);
                }
                int cc = f ? 15 : 255;
                p.setColor(Color.argb((int) (255 * ddAlpha), cc, cc, cc));
                c.drawText(inputs.get(i).label, x + 36 * U, ry0 + 47 * U, p);
            }
        }
    }
}
