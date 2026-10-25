package app.cleanyourtv.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FtpActivity extends Activity {
    static final String[] VIDEO = {"mkv", "mp4", "avi", "mov", "m4v", "ts", "webm", "wmv", "flv", "mpg", "mpeg", "m2ts", "3gp", "vob"};
    static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2})");
    static final Pattern NGX = Pattern.compile("(\\d{1,2})-([A-Za-z]{3})-(\\d{4}) (\\d{2}):(\\d{2})");
    static final Pattern HREF = Pattern.compile("href=\"([^\"#?]+)\"");

    static class Item {
        String name, url;
        boolean dir, sortRow;
        long date;
    }

    private Lister view;
    private final List<String> stack = new ArrayList<>();
    private final List<Integer> sels = new ArrayList<>();
    private String rootTitle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        rootTitle = getIntent().getStringExtra("title");
        stack.add(getIntent().getStringExtra("url"));
        view = new Lister(this);
        setContentView(view);
        view.load(stack.get(0));
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            int s = sels.isEmpty() ? 0 : sels.remove(sels.size() - 1);
            view.load(stack.get(stack.size() - 1), s);
        } else finish();
    }

    String titleFor(String url) {
        if (stack.size() <= 1) return rootTitle == null ? "Movies" : rootTitle;
        String u = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return Uri.decode(u.substring(u.lastIndexOf('/') + 1));
    }

    class Lister extends View {
        final float U;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF tmp = new RectF();
        final Typeface thin, light, reg;
        List<Item> raw = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        String title = "", status = "Loading…";
        int sel;
        float hy, hv, scroll, sv, glow;
        long last;
        int token;

        Lister(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
            thin = font(200);
            light = font(300);
            reg = font(400);
        }

        Typeface font(int w) {
            try {
                return Typeface.create(w >= 400 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
            } catch (Throwable t) {
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            }
        }

        void load(String url) { load(url, 0); }

        void load(final String url, final int restore) {
            final int my = ++token;
            title = titleFor(url);
            status = "Loading…";
            items = new ArrayList<>();
            invalidate();
            new Thread(new Runnable() {
                public void run() {
                    final List<Item> res = new ArrayList<>();
                    String err = null;
                    try {
                        HttpURLConnection cn = (HttpURLConnection) new URL(url).openConnection();
                        cn.setConnectTimeout(12000);
                        cn.setReadTimeout(20000);
                        InputStream in = cn.getInputStream();
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384];
                        int n, total = 0;
                        while ((n = in.read(buf)) > 0 && total < 8 << 20) { bo.write(buf, 0, n); total += n; }
                        in.close();
                        parse(url, new String(bo.toByteArray(), "UTF-8"), res);
                        if (res.isEmpty()) err = "Nothing to show here";
                    } catch (Throwable t) {
                        err = "Could not reach the server";
                    }
                    final String fe = err;
                    post(new Runnable() {
                        public void run() {
                            if (my != token) return;
                            raw = res;
                            items = build();
                            status = fe == null ? "" : fe;
                            sel = Math.max(0, Math.min(restore, items.size() - 1));
                            if (restore == 0 && items.size() > 1) sel = 1;
                            hy = rowY(sel); hv = 0;
                            scroll = target(); sv = 0;
                            invalidate();
                        }
                    });
                }
            }).start();
        }

        final String[] SORTS = {"Default", "Name A to Z", "Name Z to A", "Newest first", "Oldest first"};
        int mode = getSharedPreferences("ftp", 0).getInt("sort", 0);

        List<Item> build() {
            List<Item> l = new ArrayList<>(raw);
            if (mode == 1 || mode == 2) {
                java.util.Collections.sort(l, new java.util.Comparator<Item>() {
                    public int compare(Item a, Item b) { return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) * (mode == 1 ? 1 : -1); }
                });
            } else if (mode == 3 || mode == 4) {
                java.util.Collections.sort(l, new java.util.Comparator<Item>() {
                    public int compare(Item a, Item b) {
                        int c = a.date < b.date ? -1 : a.date > b.date ? 1 : 0;
                        return mode == 3 ? -c : c;
                    }
                });
            }
            if (raw.size() > 1) {
                Item h = new Item();
                h.sortRow = true;
                h.name = "Sort:  " + SORTS[mode];
                l.add(0, h);
            }
            return l;
        }

        void cycleSort() {
            mode = (mode + 1) % SORTS.length;
            getSharedPreferences("ftp", 0).edit().putInt("sort", mode).apply();
            items = build();
            sel = 0;
        }

        long parseDate(String t) {
            Matcher a = ISO.matcher(t);
            if (a.find()) return Long.parseLong(a.group(1) + a.group(2) + a.group(3) + a.group(4) + a.group(5));
            Matcher b = NGX.matcher(t);
            if (b.find()) {
                int mo = "janfebmaraprmayjunjulaugsepoctnovdec".indexOf(b.group(2).toLowerCase()) / 3 + 1;
                return Long.parseLong(b.group(3)) * 100000000L + mo * 1000000L + Long.parseLong(b.group(1)) * 10000L + Long.parseLong(b.group(4)) * 100 + Long.parseLong(b.group(5));
            }
            return 0;
        }

        void parse(String base, String html, List<Item> out) throws Exception {
            URL b = new URL(base);
            List<Item> dirs = new ArrayList<>(), files = new ArrayList<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            Matcher m = HREF.matcher(html);
            while (m.find()) {
                String h = m.group(1);
                if (h.startsWith("..") || h.contains("_h5ai")) continue;
                String full;
                try { full = new URL(b, h).toString(); } catch (Throwable t) { continue; }
                if (!full.startsWith(base) || full.length() <= base.length()) continue;
                String rest = full.substring(base.length());
                int sl = rest.indexOf('/');
                if (sl >= 0 && sl != rest.length() - 1) continue;
                if (!seen.add(full)) continue;
                Item it = new Item();
                it.url = full;
                it.dir = sl >= 0;
                it.name = Uri.decode(it.dir ? rest.substring(0, rest.length() - 1) : rest);
                int e = Math.min(html.length(), m.end() + 400);
                int nx = html.indexOf("href=", m.end());
                if (nx > 0 && nx < e) e = nx;
                it.date = parseDate(html.substring(m.end(), e));
                if (it.dir) dirs.add(it);
                else if (isVideo(it.name)) files.add(it);
            }
            out.addAll(dirs);
            out.addAll(files);
        }

        boolean isVideo(String n) {
            int d = n.lastIndexOf('.');
            if (d < 0) return false;
            String e = n.substring(d + 1).toLowerCase();
            for (String v : VIDEO) if (v.equals(e)) return true;
            return false;
        }

        float rowH() { return 92 * U; }
        float rowY(int i) { return 300 * U + i * rowH(); }
        float target() { return Math.max(0, rowY(sel) - 560 * U); }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: if (!items.isEmpty()) sel = sel > 0 ? sel - 1 : items.size() - 1; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (!items.isEmpty()) sel = sel < items.size() - 1 ? sel + 1 : 0; break;
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT: sel = Math.max(0, sel - 8); break;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT: sel = Math.max(0, Math.min(items.size() - 1, sel + 8)); break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() > 0 || items.isEmpty()) return true;
                    Item it = items.get(sel);
                    if (it.sortRow) { cycleSort(); break; }
                    if (it.dir) {
                        sels.add(sel);
                        stack.add(it.url);
                        load(it.url);
                    } else {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(it.url), "video/*")
                                    .setPackage("org.videolan.vlc"));
                        } catch (Throwable t) {
                            status = "VLC is not installed";
                        }
                    }
                    break;
                default: return super.onKeyDown(k, e);
            }
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            c.drawColor(0xFF0F0F0F);

            boolean run = false;
            float tgtY = rowY(sel), tgtS = target();
            float h = 1f / 120f;
            for (float rem = dt; rem > 0; rem -= h) {
                float s = Math.min(h, rem);
                hv += (420 * (tgtY - hy) - 30 * hv) * s; hy += hv * s;
                sv += (300 * (tgtS - scroll) - 34 * sv) * s; scroll += sv * s;
            }
            if (Math.abs(tgtY - hy) > 0.4f || Math.abs(hv) > 3f || Math.abs(tgtS - scroll) > 0.4f || Math.abs(sv) > 3f) run = true;
            else { hy = tgtY; scroll = tgtS; hv = sv = 0; }

            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(reg);
            p.setColor(Color.WHITE);
            p.setTextSize(64 * U);
            String t = title;
            while (t.length() > 3 && p.measureText(t) > 1500 * U) t = t.substring(0, t.length() - 2);
            c.drawText(t.equals(title) ? t : t + "…", 96 * U, 190 * U, p);
            p.setTypeface(light);
            p.setTextSize(30 * U);
            p.setColor(0x99FFFFFF);
            c.drawText(items.isEmpty() ? status : ((raw.size()) + " items   ·   Back goes up a folder"), 98 * U, 250 * U, p);

            c.save();
            c.clipRect(0, 270 * U, getWidth(), getHeight());
            c.translate(0, -scroll);
            tmp.set(64 * U, hy - 6 * U, 1856 * U, hy + rowH() - 14 * U);
            if (!items.isEmpty()) {
                p.setColor(Color.WHITE);
                c.drawRoundRect(tmp, 18 * U, 18 * U, p);
                p.setShader(null);
            }
            p.setTypeface(reg);
            p.setTextSize(38 * U);
            for (int i = 0; i < items.size(); i++) {
                float y = rowY(i);
                if (y - scroll < 250 * U - rowH() || y - scroll > getHeight()) continue;
                Item it = items.get(i);
                p.setColor(i == sel ? 0xFF0F0F0F : 0xFFDDDDDD);
                String n = it.name;
                while (n.length() > 3 && p.measureText(n) > 1560 * U) n = n.substring(0, n.length() - 2);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(n.equals(it.name) ? n : n + "…", 112 * U, y + 56 * U, p);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setColor(i == sel ? 0xFF0F0F0F : 0x88FFFFFF);
                if (it.sortRow) { c.drawText("OK to change", 1832 * U, y + 56 * U, p); continue; }
                c.drawText(it.dir ? "›" : "▶", 1832 * U, y + 56 * U, p);
                if (it.date > 0) {
                    String d = String.valueOf(it.date);
                    p.setTextSize(26 * U);
                    c.drawText(d.substring(0, 4) + "-" + d.substring(4, 6) + "-" + d.substring(6, 8), 1780 * U, y + 54 * U, p);
                    p.setTextSize(38 * U);
                }
            }
            c.restore();
            if (run) postInvalidateOnAnimation();
        }
    }
}
