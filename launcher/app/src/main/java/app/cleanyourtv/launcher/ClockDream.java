package app.cleanyourtv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.service.dreams.DreamService;
import android.view.View;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

public class ClockDream extends DreamService {
    static final String URL_ = "https://api.open-meteo.com/v1/forecast?latitude=23.81&longitude=90.41"
            + "&current_weather=true&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=auto";

    V v;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        Th.refresh(this);
        v = new V(this);
        setContentView(v);
        fetch();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        if (v != null) v.stop = true;
    }

    void fetch() {
        new Thread(new Runnable() {
            public void run() {
                while (v != null && !v.stop) {
                    try {
                        HttpURLConnection cn = (HttpURLConnection) new URL(URL_).openConnection();
                        cn.setConnectTimeout(8000);
                        cn.setReadTimeout(8000);
                        InputStream in = cn.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] b = new byte[4096];
                        int n;
                        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                        JSONObject j = new JSONObject(bo.toString("UTF-8"));
                        JSONObject cur = j.getJSONObject("current_weather");
                        v.temp = Math.round(cur.getDouble("temperature"));
                        v.code = cur.getInt("weathercode");
                        v.hi = Math.round(j.getJSONObject("daily").getJSONArray("temperature_2m_max").getDouble(0));
                        v.lo = Math.round(j.getJSONObject("daily").getJSONArray("temperature_2m_min").getDouble(0));
                        JSONObject dj = j.getJSONObject("daily");
                        for (int i = 0; i < 7; i++) {
                            v.dc[i] = dj.getJSONArray("weathercode").getInt(i);
                            v.dh[i] = Math.round(dj.getJSONArray("temperature_2m_max").getDouble(i));
                            v.dl[i] = Math.round(dj.getJSONArray("temperature_2m_min").getDouble(i));
                        }
                        v.has = true;
                    } catch (Throwable t) { }
                    try { Thread.sleep(30 * 60 * 1000L); } catch (InterruptedException e) { return; }
                }
            }
        }).start();
    }

    static String describe(int c) {
        if (c == 0) return "Clear";
        if (c <= 2) return "Partly cloudy";
        if (c == 3) return "Cloudy";
        if (c == 45 || c == 48) return "Fog";
        if (c >= 51 && c <= 57) return "Drizzle";
        if (c >= 61 && c <= 67 || c >= 80 && c <= 82) return "Rain";
        if (c >= 71 && c <= 77) return "Snow";
        if (c >= 95) return "Thunderstorm";
        return "Cloudy";
    }

    static class V extends View {
        final float U;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final android.graphics.RectF tmpR = new android.graphics.RectF();
        volatile boolean stop, has;
        volatile long temp, hi, lo;
        volatile int code;
        final int[] dc = new int[7];
        final long[] dh = new long[7], dl = new long[7];
        final SimpleDateFormat wf = new SimpleDateFormat("EEE", Locale.getDefault());
        final SimpleDateFormat tf = new SimpleDateFormat("h:mm", Locale.getDefault());
        final SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
        final Typeface thin = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        final Typeface reg = Typeface.create("sans-serif", Typeface.NORMAL);

        V(Context c) {
            super(c);
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
            postDelayed(tick, 1000);
        }

        final Runnable tick = new Runnable() {
            public void run() {
                if (stop) return;
                invalidate();
                postDelayed(this, 10000);
            }
        };

        void icon(Canvas c, float cx, float cy, float r, int code) {
            p.setStyle(Paint.Style.FILL);
            int ink = Th.c(0xFFDDDDDD);
            if (code <= 2) {
                p.setColor(0xFFFFC83D);
                c.drawCircle(cx, cy, r * 0.5f, p);
                if (code == 0) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(r * 0.08f);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        c.drawLine(cx + (float) Math.cos(a) * r * 0.68f, cy + (float) Math.sin(a) * r * 0.68f,
                                cx + (float) Math.cos(a) * r * 0.92f, cy + (float) Math.sin(a) * r * 0.92f, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    return;
                }
                cx += r * 0.2f; cy += r * 0.2f;
            }
            p.setColor(code <= 2 ? Th.c(0xFFE6E6E6) : ink);
            c.drawCircle(cx - r * 0.3f, cy + r * 0.1f, r * 0.34f, p);
            c.drawCircle(cx + r * 0.1f, cy - r * 0.1f, r * 0.42f, p);
            c.drawCircle(cx + r * 0.45f, cy + r * 0.15f, r * 0.3f, p);
            c.drawRect(cx - r * 0.3f, cy + r * 0.1f, cx + r * 0.45f, cy + r * 0.45f, p);
            if (code >= 51 && code != 3 && code != 45 && code != 48) {
                p.setColor(0xFF5CA8FF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.07f);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) c.drawLine(cx - r * 0.3f + i * r * 0.3f, cy + r * 0.6f, cx - r * 0.4f + i * r * 0.3f, cy + r * 0.85f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Th.c(0xFF0F0F0F));
            long now = System.currentTimeMillis();
            float dx = (float) Math.sin(now / 600000.0) * 8 * U, dy = (float) Math.cos(now / 700000.0) * 8 * U;
            float cx = getWidth() / 2f + dx, base = getHeight() * 0.37f + dy;
            Date d = new Date(now);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(thin);
            p.setColor(Th.W());
            p.setTextSize(360 * U);
            c.drawText(tf.format(d), cx, base, p);
            p.setTypeface(reg);
            p.setTextSize(44 * U);
            p.setColor(Th.c(0xFFAAAAAA));
            c.drawText(df.format(d), cx, base + 70 * U, p);
            if (has) {
                float y = base + 170 * U;
                String ts = temp + "\u00b0", ds = describe(code) + "   \u00b7   H " + hi + "\u00b0  L " + lo + "\u00b0";
                p.setTypeface(reg);
                p.setTextSize(64 * U);
                float tw = p.measureText(ts);
                p.setTextSize(34 * U);
                float dw = p.measureText(ds);
                float iw = 104 * U, g1 = 18 * U, g2 = 36 * U;
                float x = cx - (iw + g1 + tw + g2 + dw) / 2f;
                icon(c, x + iw / 2f, y - 26 * U, 52 * U, code);
                p.setTextAlign(Paint.Align.LEFT);
                p.setColor(Th.W());
                p.setTextSize(64 * U);
                c.drawText(ts, x + iw + g1, y, p);
                p.setTextSize(34 * U);
                p.setColor(Th.c(0xFFAAAAAA));
                c.drawText(ds, x + iw + g1 + tw + g2, y - 4 * U, p);
                float top = y + 90 * U;
                p.setColor(Th.c(0x22FFFFFF));
                p.setStyle(Paint.Style.FILL);
                float colW = 210 * U, x0 = cx - colW * 3.5f;
                tmpR.set(x0 - 10 * U, top, x0 + colW * 7 + 10 * U, top + 270 * U);
                c.drawRoundRect(tmpR, 28 * U, 28 * U, p);
                for (int i = 0; i < 7; i++) {
                    float mx = x0 + colW * (i + 0.5f);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTypeface(reg);
                    p.setTextSize(30 * U);
                    p.setColor(i == 0 ? Th.W() : Th.c(0xFFAAAAAA));
                    c.drawText(i == 0 ? "Today" : wf.format(new Date(now + i * 86400000L)), mx, top + 52 * U, p);
                    icon(c, mx, top + 130 * U, 40 * U, dc[i]);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTypeface(reg);
                    p.setTextSize(34 * U);
                    p.setColor(Th.W());
                    c.drawText(dh[i] + "°", mx - 34 * U, top + 238 * U, p);
                    p.setColor(Th.c(0xFF909090));
                    c.drawText(dl[i] + "°", mx + 34 * U, top + 238 * U, p);
                }
            }
        }
    }
}
