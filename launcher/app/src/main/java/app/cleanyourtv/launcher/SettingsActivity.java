package app.cleanyourtv.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

public class SettingsActivity extends Activity {
    static final String TV = "com.android.tv.settings";
    static final String[][] ALL = {
        {"Network & Wi-Fi", "Connect to a network", "p:wifi"},
        {"Bluetooth & remotes", "Pair a remote, headphones or gamepad", "p:bt"},
        {"Servers", "Add, hide or edit your movie servers", "servers"},
        {"Display & HDMI", "Screensaver, sleep and HDMI control", "p:display"},
        {"Theme", "", "theme"},
        {"Sound", "Volume, surround and ARC", "p:sound"},
        {"Date & time", "Clock and time zone", "p:date"},
        {"Language", "Menu and keyboard language", "p:lang"},
        {"Keyboard", "Choose the on-screen keyboard", "p:kb"},
        {"Accessibility", "Captions and text", "p:access"},
        {"Apps", "Open, inspect or remove apps", "p:apps"},
        {"Storage", "Space and memory", "p:storage"},
        {"Privacy", "Location", "p:privacy"},
        {"Developer options", "Debugging and speed", "p:dev"},
        {"About", "Device name, version and network", "p:about"},
    };

    static String[][] ROWS = ALL;

    static void filter(Context c) {
        boolean dev = false;
        try { dev = Settings.Global.getInt(c.getContentResolver(), "development_settings_enabled", 0) == 1; } catch (Throwable ignored) { }
        java.util.List<String[]> l = new java.util.ArrayList<>();
        for (String[] r : ALL) if (dev || !r[2].equals("p:dev")) l.add(r);
        ROWS = l.toArray(new String[0][]);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        filter(this);
        Th.refresh(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Th.c(0xFF0F0F0F)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new V(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        filter(this);
        Th.refresh(this);
        getWindow().getDecorView().invalidate();
    }

    void open(String t) {
        if (t.equals("theme")) { Th.setMode(this, (Th.mode(this) + 1) % 3); getWindow().setBackgroundDrawable(new ColorDrawable(Th.c(0xFF0F0F0F))); return; }
        Intent i;
        if (t.startsWith("p:")) i = PageActivity.page(this, t.substring(2));
        else if (t.equals("servers")) i = new Intent(this, ServersActivity.class);
        else if (t.equals("all")) i = new Intent(Settings.ACTION_SETTINGS);
        else i = new Intent().setComponent(new ComponentName(TV, TV + t));
        try { startActivity(i); } catch (Throwable e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Throwable ignored) { }
        }
    }

    class V extends View {
        final float U;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF tmp = new RectF();
        final Typeface reg = Typeface.create("sans-serif", Typeface.NORMAL), med = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        int sel;
        float hy, hv, scroll, sv;
        long last;

        V(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        }

        float rowY(int i) { return 300 * U + i * 100 * U; }
        float target() { return Math.max(0, rowY(sel) - 560 * U); }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) { if (sel > 0) sel--; }
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { if (sel < ROWS.length - 1) sel++; }
            else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (e.getRepeatCount() == 0) open(ROWS[sel][2]);
            } else return super.onKeyDown(k, e);
            invalidate();
            return true;
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            c.drawColor(Th.c(0xFF0F0F0F));
            float tgtY = rowY(sel), tgtS = target(), h = 1f / 120f;
            for (float rem = dt; rem > 0; rem -= h) {
                float s = Math.min(h, rem);
                hv += (420 * (tgtY - hy) - 30 * hv) * s; hy += hv * s;
                sv += (300 * (tgtS - scroll) - 34 * sv) * s; scroll += sv * s;
            }
            boolean run = Math.abs(tgtY - hy) > 0.4f || Math.abs(hv) > 3f || Math.abs(tgtS - scroll) > 0.4f || Math.abs(sv) > 3f;
            if (!run) { hy = tgtY; scroll = tgtS; hv = sv = 0; }
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(reg);
            p.setColor(Th.W());
            p.setTextSize(64 * U);
            c.drawText("Settings", 96 * U, 190 * U, p);
            c.save();
            c.clipRect(0, 230 * U, getWidth(), getHeight());
            c.translate(0, -scroll);
            tmp.set(64 * U, hy - 6 * U, 1856 * U, hy + 90 * U);
            p.setColor(Th.W());
            c.drawRoundRect(tmp, 18 * U, 18 * U, p);
            for (int i = 0; i < ROWS.length; i++) {
                float y = rowY(i);
                if (y - scroll < 150 * U || y - scroll > getHeight()) continue;
                boolean on = i == sel;
                p.setTypeface(med);
                p.setTextSize(36 * U);
                p.setColor(on ? Th.c(0xFF0F0F0F) : Th.c(0xFFDDDDDD));
                c.drawText(ROWS[i][0], 112 * U, y + 38 * U, p);
                p.setTypeface(reg);
                p.setTextSize(26 * U);
                p.setColor(on ? Th.c(0xFF444444) : Th.c(0x88FFFFFF));
                c.drawText(ROWS[i][2].equals("theme") ? new String[]{"Auto  (light by day, dark at night)", "Dark", "Light"}[Th.mode(getContext())] + "   ·   press OK to change" : ROWS[i][1], 112 * U, y + 72 * U, p);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTextSize(40 * U);
                c.drawText("›", 1832 * U, y + 52 * U, p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            c.restore();
            if (run) postInvalidateOnAnimation();
        }
    }
}
