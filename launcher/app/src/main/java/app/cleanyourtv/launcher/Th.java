package app.cleanyourtv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.provider.Settings;

import java.util.Calendar;

final class Th {
    static boolean light;

    static int mode(Context c) {
        return c.getSharedPreferences("theme", 0).getInt("mode", 0);
    }

    static void setMode(Context c, int m) {
        c.getSharedPreferences("theme", 0).edit().putInt("mode", m).apply();
        refresh(c);
    }

    static boolean refresh(Context c) {
        int m = mode(c);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean l = m == 2 || (m == 0 && h >= 7 && h < 18);
        boolean changed = l != light;
        light = l;
        try {
            Settings.Secure.putInt(c.getContentResolver(), "ui_night_mode", l ? 1 : 2);
            ((android.app.UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE)).setNightMode(l ? 1 : 2);
        } catch (Throwable ignored) { }
        return changed;
    }

    static int c(int v) {
        if (!light) return v;
        int r = (v >> 16) & 255, g = (v >> 8) & 255, b = v & 255;
        if (r != g || g != b) return v;
        int n = Math.max(15, Math.min(240, 255 - r));
        return (v & 0xFF000000) | (n << 16) | (n << 8) | n;
    }

    static int W() { return c(Color.WHITE); }
    static int K() { return c(Color.BLACK); }
}
