package app.cleanyourtv.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

class Servers {
    static class S {
        String name, url;
        boolean on = true;
    }

    static String clean(String s) { return s.replace('\t', ' ').replace('\n', ' ').trim(); }

    static String normUrl(String u) {
        u = clean(u);
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        if (!u.endsWith("/")) u += "/";
        return u;
    }

    static List<S> load(Context c) {
        SharedPreferences sp = c.getSharedPreferences("servers", 0);
        String raw = sp.getString("list", null);
        List<S> out = new ArrayList<>();
        if (raw == null) {
            for (String s : c.getResources().getStringArray(R.array.ftp_servers)) {
                String[] kv = s.split("\\|", 2);
                if (kv.length < 2) continue;
                S x = new S();
                x.name = kv[0];
                x.url = kv[1];
                out.add(x);
            }
            save(c, out);
            return out;
        }
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t", 3);
            if (f.length < 3) continue;
            S x = new S();
            x.name = f[0];
            x.url = f[1];
            x.on = f[2].equals("1");
            out.add(x);
        }
        return out;
    }

    static void save(Context c, List<S> l) {
        StringBuilder sb = new StringBuilder();
        for (S x : l) sb.append(clean(x.name)).append('\t').append(clean(x.url)).append('\t').append(x.on ? "1" : "0").append('\n');
        c.getSharedPreferences("servers", 0).edit().putString("list", sb.toString()).apply();
    }
}
