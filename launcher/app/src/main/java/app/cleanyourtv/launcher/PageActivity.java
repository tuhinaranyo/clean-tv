package app.cleanyourtv.launcher;

import android.app.Activity;
import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Set;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.DhcpInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageActivity extends Activity {
    static final int ACTION = 0, TOGGLE = 1, ADJUST = 2, HEADER = 3, INFO = 4;

    interface Adj { void by(int dir); }

    static class Row {
        int kind;
        String t, s, v;
        boolean on;
        Runnable act;
        Adj adj;
        int level = -999;
        boolean lock;
    }

    static Intent page(Context c, String name) {
        return new Intent(c, PageActivity.class).putExtra("page", name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    String page;
    WifiManager wm;
    V view;
    final List<Row> rows = new ArrayList<>();
    final Handler ui = new Handler();
    ContentResolver cr;
    final BroadcastReceiver rx = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (BluetoothDevice.ACTION_FOUND.equals(i.getAction())) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null && d.getBondState() != BluetoothDevice.BOND_BONDED) {
                    boolean have = false;
                    for (BluetoothDevice x : btFound) if (x.getAddress().equals(d.getAddress())) have = true;
                    if (!have) btFound.add(d);
                }
            }
            refresh();
        }
    };
    final Runnable rescan = new Runnable() {
        public void run() {
            try { wm.startScan(); } catch (Throwable ignored) { }
            ui.postDelayed(this, 12000);
        }
    };
    String wifiNote = "";
    int startSel = -1;
    final List<BluetoothDevice> btFound = new ArrayList<>();
    BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Th.refresh(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Th.c(0xFF0F0F0F)));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        page = getIntent().getStringExtra("page");
        if (page == null) page = "about";
        cr = getContentResolver();
        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        view = new V(this);
        setContentView(view);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(rx, f);
        if (page.equals("wifi")) ui.post(rescan);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { if (bt != null && bt.isDiscovering()) bt.cancelDiscovery(); } catch (Throwable ignored) { }
        ui.removeCallbacks(rescan);
        try { unregisterReceiver(rx); } catch (Throwable ignored) { }
    }

    // ---------- helpers ----------
    Row row(int kind, String t, String s, String v) {
        Row r = new Row();
        r.kind = kind; r.t = t; r.s = s; r.v = v;
        rows.add(r);
        return r;
    }
    Row header(String t) { return row(HEADER, t, null, null); }
    Row info(String t, String v) { return row(INFO, t, null, v); }

    Row toggle(String t, String s, boolean on, Runnable act) {
        Row r = row(TOGGLE, t, s, null);
        r.on = on; r.act = act;
        return r;
    }

    int gInt(String k, int d) { try { return Settings.Global.getInt(cr, k, d); } catch (Throwable e) { return d; } }
    int sInt(String k, int d) { try { return Settings.Secure.getInt(cr, k, d); } catch (Throwable e) { return d; } }
    int yInt(String k, int d) { try { return Settings.System.getInt(cr, k, d); } catch (Throwable e) { return d; } }
    void gPut(String k, int v) { try { Settings.Global.putInt(cr, k, v); } catch (Throwable e) { note("Not allowed"); } }
    void sPut(String k, int v) { try { Settings.Secure.putInt(cr, k, v); } catch (Throwable e) { note("Not allowed"); } }
    void yPut(String k, int v) { try { Settings.System.putInt(cr, k, v); } catch (Throwable e) { note("Needs permission"); } }
    void note(String s) { wifiNote = s; }

    boolean locationOn() { return sInt("location_mode", 0) != 0; }

    static String strip(String s) { return s == null ? "" : s.replace("\"", ""); }

    String size(long b) {
        if (b > 1L << 30) return String.format("%.1f GB", b / (double) (1L << 30));
        return Math.round(b / (double) (1L << 20)) + " MB";
    }

    void choose(String title, String[] items, DialogInterface.OnClickListener l) {
        new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle(title).setItems(items, l).show();
    }

    void ask(String title, String msg, String ok, final Runnable yes) {
        new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle(title).setMessage(msg)
                .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { yes.run(); refresh(); }
                }).setNegativeButton("Cancel", null).show();
    }

    EditText field(String hint, boolean pass) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(Th.W());
        e.setHintTextColor(Th.c(0x88FFFFFF));
        if (pass) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    String title() {
        switch (page) {
            case "wifi": return "Wi-Fi";
            case "sound": return "Sound";
            case "date": return "Date & time";
            case "apps": return "Apps";
            case "storage": return "Storage";
            case "privacy": return "Privacy";
            case "dev": return "Developer options";
            case "tz": return "Time zone";
            case "lang": return "Language";
            case "kb": return "Keyboard";
            case "bt": return "Bluetooth & remotes";
            case "display": return "Display & HDMI";
            case "access": return "Accessibility";
            default: return "About";
        }
    }

    // ---------- pages ----------
    void refresh() {
        int keep = view == null ? 0 : view.sel;
        rows.clear();
        try {
            switch (page) {
                case "wifi": buildWifi(); break;
                case "sound": buildSound(); break;
                case "date": buildDate(); break;
                case "apps": buildApps(); break;
                case "storage": buildStorage(); break;
                case "privacy": buildPrivacy(); break;
                case "dev": buildDev(); break;
                case "tz": buildTz(); break;
                case "lang": buildLang(); break;
                case "kb": buildKb(); break;
                case "bt": buildBt(); break;
                case "display": buildDisplay(); break;
                case "access": buildAccess(); break;
                default: buildAbout();
            }
        } catch (Throwable t) { info("Something went wrong", String.valueOf(t.getMessage())); }
        if (view != null) {
            view.sel = startSel >= 0 ? startSel : keep;
            if (startSel >= 0) { view.snap = true; startSel = -1; }
            view.fix();
            view.invalidate();
        }
    }

    static class Net {
        String ssid, caps;
        int level, saved = -1;
        boolean secured, connected;
    }

    void buildWifi() {
        toggle("Wi-Fi", wm.isWifiEnabled() ? "On" : "Off", wm.isWifiEnabled(), new Runnable() {
            public void run() { wm.setWifiEnabled(!wm.isWifiEnabled()); ui.postDelayed(new Runnable() { public void run() { refresh(); } }, 800); }
        });
        if (!wm.isWifiEnabled()) return;
        if (!locationOn()) {
            Row r = row(ACTION, "Turn on Location to find networks", "Android only lists nearby Wi-Fi networks while Location is on", null);
            r.act = new Runnable() { public void run() {
                ask("Turn on Location?", "Location is needed by Android to list nearby Wi-Fi networks. This TV does not share your location with this launcher.", "Turn on",
                        new Runnable() { public void run() { sPut("location_mode", 3); } });
            } };
        }
        WifiInfo wi = wm.getConnectionInfo();
        String cur = wi != null && wi.getNetworkId() >= 0 ? strip(wi.getSSID()) : "";
        boolean linked = wi != null && wi.getNetworkId() >= 0;
        if (linked) {
            final WifiInfo w = wi;
            final DhcpInfo d = wm.getDhcpInfo();
            Row r = row(ACTION, "Network details", (cur.isEmpty() || cur.contains("unknown") ? "Connected" : "Connected to " + cur) + "  ·  " + ip(wi.getIpAddress()), null);
            r.act = new Runnable() { public void run() {
                String m = "IP address   " + ip(w.getIpAddress())
                        + (d != null ? "\nGateway   " + ip(d.gateway) + "\nDNS   " + ip(d.dns1) : "")
                        + "\nLink speed   " + w.getLinkSpeed() + " Mbps"
                        + "\nSignal   " + WifiManager.calculateSignalLevel(w.getRssi(), 5) * 25 + "%  (" + w.getRssi() + " dBm)"
                        + "\nFrequency   " + w.getFrequency() + " MHz";
                new AlertDialog.Builder(PageActivity.this, R.style.Dark_Dialog).setTitle("Network details").setMessage(m).setPositiveButton("Close", null).show();
            } };
        }
        Map<String, Integer> saved = new HashMap<>();
        try {
            List<WifiConfiguration> cfg = wm.getConfiguredNetworks();
            if (cfg != null) for (WifiConfiguration w : cfg) saved.put(strip(w.SSID), w.networkId);
        } catch (Throwable ignored) { }
        Map<String, Net> best = new HashMap<>();
        List<ScanResult> res = null;
        try { res = wm.getScanResults(); } catch (Throwable ignored) { }
        if (res != null) for (ScanResult r : res) {
            if (r.SSID == null || r.SSID.isEmpty()) continue;
            Net o = best.get(r.SSID);
            if (o != null && o.level >= r.level) continue;
            Net n = new Net();
            n.ssid = r.SSID; n.caps = r.capabilities; n.level = r.level;
            n.secured = r.capabilities.contains("WEP") || r.capabilities.contains("PSK") || r.capabilities.contains("EAP");
            best.put(r.SSID, n);
        }
        List<Net> nets = new ArrayList<>(best.values());
        for (Net n : nets) { n.connected = linked && n.ssid.equals(cur); Integer id = saved.get(n.ssid); if (id != null) n.saved = id; }
        Collections.sort(nets, new Comparator<Net>() {
            public int compare(Net a, Net b) {
                if (a.connected != b.connected) return a.connected ? -1 : 1;
                if ((a.saved >= 0) != (b.saved >= 0)) return a.saved >= 0 ? -1 : 1;
                return b.level - a.level;
            }
        });
        header(nets.isEmpty() ? "Searching for networks…" : "Available networks");
        for (final Net n : nets) {
            Row r = row(ACTION, n.ssid, n.connected ? "Connected" : n.saved >= 0 ? "Saved" : n.secured ? "Secured" : "Open", null);
            r.level = n.level; r.lock = n.secured;
            r.act = new Runnable() { public void run() { pick(n); } };
        }
        Row h = row(ACTION, "Add a hidden network", "Enter the name and password yourself", null);
        h.act = new Runnable() { public void run() { hidden(); } };
        if (!saved.isEmpty()) {
            Row f = row(ACTION, "Forget all saved networks", saved.size() + " saved", null);
            f.act = new Runnable() { public void run() {
                ask("Forget all networks?", "This TV will not reconnect to any saved Wi-Fi.", "Forget", new Runnable() { public void run() {
                    try { for (WifiConfiguration w : wm.getConfiguredNetworks()) wm.removeNetwork(w.networkId); } catch (Throwable ignored) { }
                } });
            } };
        }
    }

    String ip(int a) { return (a & 255) + "." + (a >> 8 & 255) + "." + (a >> 16 & 255) + "." + (a >> 24 & 255); }

    void pick(final Net n) {
        if (n.connected || n.saved >= 0) {
            String first = n.connected ? "Disconnect" : "Connect";
            choose(n.ssid, new String[]{first, "Forget this network"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (w == 0) { if (n.connected) wm.disconnect(); else { wm.disconnect(); wm.enableNetwork(n.saved, true); wm.reconnect(); } }
                    else { if (n.saved >= 0) wm.removeNetwork(n.saved); if (n.connected) wm.disconnect(); }
                    refresh();
                }
            });
        } else if (!n.secured) join(n.ssid, null, n.caps);
        else {
            final EditText pw = field("Password", true);
            new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle("Password for " + n.ssid).setView(pw)
                    .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { join(n.ssid, pw.getText().toString(), n.caps); }
                    }).setNegativeButton("Cancel", null).show();
        }
    }

    void hidden() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText n = field("Network name", false), p = field("Password (leave empty if open)", true);
        box.addView(n);
        box.addView(p);
        new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle("Add hidden network").setView(box)
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String s = n.getText().toString().trim(), pw = p.getText().toString();
                        if (!s.isEmpty()) join(s, pw.isEmpty() ? null : pw, "WPA2-PSK", true);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void join(String ssid, String pass, String caps) { join(ssid, pass, caps, false); }

    void join(String ssid, String pass, String caps, boolean hid) {
        WifiConfiguration c = new WifiConfiguration();
        c.SSID = "\"" + ssid + "\"";
        c.hiddenSSID = hid;
        if (pass == null) c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        else if (caps.contains("WEP")) {
            c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            c.wepKeys[0] = "\"" + pass + "\"";
            c.wepTxKeyIndex = 0;
        } else c.preSharedKey = "\"" + pass + "\"";
        int id = wm.addNetwork(c);
        if (id < 0) { refresh(); return; }
        wm.disconnect();
        wm.enableNetwork(id, true);
        wm.reconnect();
        refresh();
    }

    void buildSound() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        Row v = row(ADJUST, "Volume", "Use Left and Right to change", cur + " / " + max);
        v.adj = new Adj() { public void by(int dir) {
            int m = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(m, am.getStreamVolume(AudioManager.STREAM_MUSIC) + dir)), 0);
        } };
        final boolean fx = yInt("sound_effects_enabled", 1) == 1;
        toggle("System sounds", "Clicks when you move around the TV", fx, new Runnable() { public void run() { yPut("sound_effects_enabled", fx ? 0 : 1); } });
        boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
        toggle("Mute", "Silence the TV", muted, new Runnable() { public void run() {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0);
        } });
        final int sur = gInt("encoded_surround_output", 0);
        Row m = row(ACTION, "Surround sound", "Dolby / DTS output to a soundbar or receiver", new String[]{"Auto", "Never", "Always"}[Math.max(0, Math.min(2, sur))]);
        m.act = new Runnable() { public void run() {
            choose("Surround sound", new String[]{"Auto", "Never", "Always"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { gPut("encoded_surround_output", w); refresh(); }
            });
        } };
        final boolean arc = gInt("hdmi_arc_control_enabled", 1) == 1;
        toggle("HDMI ARC", "Send TV sound to a soundbar over HDMI", arc, new Runnable() { public void run() { gPut("hdmi_arc_control_enabled", arc ? 0 : 1); } });
    }

    void buildDate() {
        Row t = info("Current time", android.text.format.DateFormat.format("EEEE, d MMMM yyyy  HH:mm", System.currentTimeMillis()).toString());
        final boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        toggle("24-hour clock", h24 ? "13:00" : "1:00 PM", h24, new Runnable() { public void run() { yPut("time_12_24", h24 ? 12 : 24); } });
        final boolean at = gInt("auto_time", 1) == 1;
        toggle("Automatic date & time", "Use network time", at, new Runnable() { public void run() { gPut("auto_time", at ? 0 : 1); } });
        final boolean az = gInt("auto_time_zone", 1) == 1;
        toggle("Automatic time zone", "Use network time zone", az, new Runnable() { public void run() { gPut("auto_time_zone", az ? 0 : 1); } });
        Row z = row(ACTION, "Time zone", "Pick your city", java.util.TimeZone.getDefault().getID().replace('_', ' '));
        z.act = new Runnable() { public void run() { startActivity(page(PageActivity.this, "tz")); } };
    }

    void buildStorage() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        long total = s.getTotalBytes(), free = s.getAvailableBytes();
        header("Internal storage");
        info("Total", size(total));
        info("Used", size(total - free));
        info("Free", size(free));
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        header("Memory");
        info("Total", size(mi.totalMem));
        info("Available", size(mi.availMem));
        header("External drives");
        boolean any = false;
        try {
            java.io.File[] ds = getExternalFilesDirs(null);
            for (int i = 1; ds != null && i < ds.length; i++) if (ds[i] != null) {
                any = true;
                StatFs e = new StatFs(ds[i].getPath());
                info("USB drive " + i, size(e.getAvailableBytes()) + " free of " + size(e.getTotalBytes()));
            }
        } catch (Throwable ignored) { }
        if (!any) info("USB drive", "None connected");
    }

    void buildPrivacy() {
        final int mode = sInt("location_mode", 0);
        toggle("Location", "Needed to find Wi-Fi and Bluetooth devices", mode != 0, new Runnable() { public void run() { sPut("location_mode", mode != 0 ? 0 : 3); } });
        final boolean sc = sInt("screensaver_enabled", 1) == 1;
        info("Ads and personalisation", "Managed by each app");
        info("Usage data", "Not collected by this launcher");
    }

    void buildDev() {
        final boolean dev = gInt("development_settings_enabled", 0) == 1;
        toggle("Developer options", "Show advanced options", dev, new Runnable() { public void run() { gPut("development_settings_enabled", dev ? 0 : 1); } });
        final boolean adb = gInt("adb_enabled", 0) == 1;
        toggle("USB debugging", "Lets a computer on this network control the TV", adb, new Runnable() { public void run() {
            if (adb) ask("Turn off debugging?", "You will not be able to connect from a computer until it is turned on again.", "Turn off", new Runnable() { public void run() { gPut("adb_enabled", 0); } });
            else gPut("adb_enabled", 1);
        } });
        final boolean awake = gInt("stay_on_while_plugged_in", 0) != 0;
        toggle("Stay awake", "Keep the screen on while plugged in", awake, new Runnable() { public void run() { gPut("stay_on_while_plugged_in", awake ? 0 : 3); } });
        float sc = 1f;
        try { sc = Settings.Global.getFloat(cr, "window_animation_scale", 1f); } catch (Throwable ignored) { }
        final float cur = sc;
        Row a = row(ACTION, "Animation speed", "Lower is snappier. Applies to every app.", cur == 0 ? "Off" : cur + "x");
        a.act = new Runnable() { public void run() {
            choose("Animation speed", new String[]{"Normal (1x)", "Fast (0.5x)", "Off"}, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    float v = w == 0 ? 1f : w == 1 ? 0.5f : 0f;
                    try {
                        Settings.Global.putFloat(cr, "window_animation_scale", v);
                        Settings.Global.putFloat(cr, "transition_animation_scale", v);
                        Settings.Global.putFloat(cr, "animator_duration_scale", v);
                    } catch (Throwable e) { note("Not allowed"); }
                    refresh();
                }
            });
        } };
        final boolean bg = gInt("always_finish_activities", 0) == 1;
        toggle("Don't keep activities", "Frees memory but may close apps", bg, new Runnable() { public void run() { gPut("always_finish_activities", bg ? 0 : 1); } });
        final boolean ver = gInt("verifier_verify_adb_installs", 1) == 1;
        toggle("Verify apps over USB", "Scan apps installed with a computer", ver, new Runnable() { public void run() { gPut("verifier_verify_adb_installs", ver ? 0 : 1); } });
        final boolean wifiadb = gInt("adb_wifi_enabled", 0) == 1;
        header("Debug");
        info("Debug address", ip(wm.getConnectionInfo().getIpAddress()) + ":5555");
    }

    void buildAbout() {
        final String name = Settings.Global.getString(cr, "device_name");
        Row n = row(ACTION, "Device name", "How this TV appears to other devices", name == null ? Build.MODEL : name);
        n.act = new Runnable() { public void run() {
            final EditText e = field("Device name", false);
            e.setText(name == null ? Build.MODEL : name);
            new AlertDialog.Builder(PageActivity.this, R.style.Dark_Dialog).setTitle("Device name").setView(e)
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try { Settings.Global.putString(cr, "device_name", e.getText().toString().trim()); } catch (Throwable ignored) { }
                            refresh();
                        }
                    }).setNegativeButton("Cancel", null).show();
        } };
        header("System");
        info("Model", Build.MODEL);
        info("Manufacturer", Build.MANUFACTURER);
        info("Android version", Build.VERSION.RELEASE);
        info("Security patch", Build.VERSION.SECURITY_PATCH);
        info("Build", Build.DISPLAY);
        info("Kernel", System.getProperty("os.version"));
        info("Uptime", (SystemClock.elapsedRealtime() / 3600000) + " h " + (SystemClock.elapsedRealtime() / 60000 % 60) + " min");
        header("Network");
        WifiInfo wi = wm.getConnectionInfo();
        info("IP address", wi != null && wi.getNetworkId() >= 0 ? ip(wi.getIpAddress()) : "Not connected");
        header("Launcher");
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            info("Version", pi.versionName);
        } catch (Throwable ignored) { }
    }

    void buildApps() {
        final PackageManager pm = getPackageManager();
        List<ResolveInfo> l = new ArrayList<>();
        l.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        l.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<ResolveInfo> uniq = new ArrayList<>();
        for (ResolveInfo ri : l) if (seen.add(ri.activityInfo.packageName)) uniq.add(ri);
        Collections.sort(uniq, new Comparator<ResolveInfo>() {
            public int compare(ResolveInfo a, ResolveInfo b) { return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))); }
        });
        header(uniq.size() + " apps");
        for (final ResolveInfo ri : uniq) {
            final String pkg = ri.activityInfo.packageName;
            boolean sys = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            String ver = "";
            try { ver = pm.getPackageInfo(pkg, 0).versionName; } catch (Throwable ignored) { }
            Row r = row(ACTION, String.valueOf(ri.loadLabel(pm)), (sys ? "System app" : "Installed app") + (ver == null ? "" : "  ·  " + ver), null);
            final boolean fs = sys;
            r.act = new Runnable() { public void run() {
                choose(String.valueOf(ri.loadLabel(pm)), fs ? new String[]{"Open", "Details"} : new String[]{"Open", "Details", "Uninstall"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            if (w == 0) startActivity(pm.getLaunchIntentForPackage(pkg) != null ? pm.getLaunchIntentForPackage(pkg) : new Intent());
                            else if (w == 1) details(pkg, String.valueOf(ri.loadLabel(pm)));
                            else startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                        } catch (Throwable ignored) { }
                    }
                });
            } };
        }
    }

    void details(String pkg, String label) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
            String m = "Package   " + pkg + "\nVersion   " + pi.versionName
                    + "\nSize   " + size(new java.io.File(pi.applicationInfo.sourceDir).length())
                    + "\nInstalled   " + android.text.format.DateFormat.format("d MMM yyyy", pi.firstInstallTime)
                    + "\nUpdated   " + android.text.format.DateFormat.format("d MMM yyyy", pi.lastUpdateTime)
                    + "\nPermissions   " + (pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length);
            new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle(label).setMessage(m).setPositiveButton("Close", null).show();
        } catch (Throwable ignored) { }
    }

    void buildTz() {
        final String cur = TimeZone.getDefault().getID();
        List<String> ids = new ArrayList<>();
        for (String id : TimeZone.getAvailableIDs()) {
            if (!id.contains("/") || id.startsWith("Etc/") || id.startsWith("SystemV/") || id.startsWith("US/") || id.startsWith("Canada/")
                    || id.startsWith("Brazil/") || id.startsWith("Chile/") || id.startsWith("Mexico/")) continue;
            ids.add(id);
        }
        Collections.sort(ids, new Comparator<String>() {
            public int compare(String a, String b) {
                int d = TimeZone.getTimeZone(a).getRawOffset() - TimeZone.getTimeZone(b).getRawOffset();
                return d != 0 ? d : a.compareTo(b);
            }
        });
        header("Left and Right jump through the list");
        for (final String id : ids) {
            int off = TimeZone.getTimeZone(id).getRawOffset() / 60000;
            String gmt = String.format("GMT%s%02d:%02d", off < 0 ? "-" : "+", Math.abs(off) / 60, Math.abs(off) % 60);
            String city = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
            String region = id.substring(0, id.indexOf('/')).replace('_', ' ');
            Row r = row(ACTION, city, region + "  ·  " + gmt + (id.equals(cur) ? "  ·  Current" : ""), null);
            if (id.equals(cur) && startSel < 0 && view != null && !view.positioned) { startSel = rows.size() - 1; view.positioned = true; }
            r.act = new Runnable() { public void run() {
                gPut("auto_time_zone", 0);
                try { ((AlarmManager) getSystemService(ALARM_SERVICE)).setTimeZone(id); } catch (Throwable e) { note("Could not change"); }
                finish();
            } };
        }
    }

    static final String[][] LANGS = {
        {"en", "US"}, {"en", "GB"}, {"en", "IN"}, {"bn", "BD"}, {"bn", "IN"}, {"hi", "IN"}, {"ur", "PK"}, {"ta", "IN"}, {"ne", "NP"},
        {"ar", "SA"}, {"fa", "IR"}, {"tr", "TR"}, {"ru", "RU"}, {"es", "ES"}, {"es", "US"}, {"fr", "FR"}, {"de", "DE"}, {"it", "IT"},
        {"pt", "BR"}, {"pt", "PT"}, {"nl", "NL"}, {"pl", "PL"}, {"id", "ID"}, {"ms", "MY"}, {"th", "TH"}, {"vi", "VN"},
        {"ja", "JP"}, {"ko", "KR"}, {"zh", "CN"}, {"zh", "TW"}
    };

    void buildLang() {
        Locale cur = Locale.getDefault();
        for (final String[] l : LANGS) {
            final Locale loc = new Locale(l[0], l[1]);
            boolean on = cur.getLanguage().equals(l[0]) && cur.getCountry().equals(l[1]);
            Row r = row(ACTION, loc.getDisplayLanguage(loc) + " (" + loc.getDisplayCountry(loc) + ")", loc.getDisplayName(Locale.ENGLISH) + (on ? "  ·  Current" : ""), null);
            if (on && view != null && !view.positioned) { startSel = rows.size() - 1; view.positioned = true; }
            r.act = new Runnable() { public void run() {
                try {
                    Class<?> am = Class.forName("android.app.IActivityManager");
                    Object svc = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
                    Configuration c = (Configuration) am.getMethod("getConfiguration").invoke(svc);
                    c.setLocales(new LocaleList(loc));
                    Configuration.class.getField("userSetLocale").setBoolean(c, true);
                    am.getMethod("updatePersistentConfiguration", Configuration.class).invoke(svc, c);
                } catch (Throwable e) { note("Could not change language"); }
            } };
        }
    }

    List<String> enabledIme() {
        String v = Settings.Secure.getString(cr, "enabled_input_methods");
        List<String> l = new ArrayList<>();
        if (v != null && !v.isEmpty()) for (String x : v.split(":")) if (!x.isEmpty()) l.add(x);
        return l;
    }

    void buildKb() {
        final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        final String def = Settings.Secure.getString(cr, "default_input_method");
        final PackageManager pm = getPackageManager();
        header("Installed keyboards");
        for (final InputMethodInfo i : imm.getInputMethodList()) {
            final String id = i.getId();
            boolean isDef = id.equals(def), en = false;
            for (String e : enabledIme()) if (e.equals(id) || e.startsWith(id + ";")) en = true;
            final boolean enabled = en;
            Row r = row(ACTION, String.valueOf(i.loadLabel(pm)), isDef ? "Default" : en ? "Enabled" : "Off", null);
            r.act = new Runnable() { public void run() {
                choose(String.valueOf(i.loadLabel(pm)), new String[]{"Use as default", enabled ? "Turn off" : "Turn on"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        List<String> en2 = enabledIme();
                        if (w == 0) {
                            if (!enabled) en2.add(id);
                            joinIme(en2);
                            try { Settings.Secure.putString(cr, "default_input_method", id); } catch (Throwable e) { note("Not allowed"); }
                        } else if (enabled) {
                            if (id.equals(def)) { note("Pick another default first"); }
                            else { List<String> n = new ArrayList<>(); for (String e : en2) if (!(e.equals(id) || e.startsWith(id + ";"))) n.add(e); joinIme(n); }
                        } else { en2.add(id); joinIme(en2); }
                        refresh();
                    }
                });
            } };
        }
    }

    void joinIme(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String x : l) { if (b.length() > 0) b.append(':'); b.append(x); }
        try { Settings.Secure.putString(cr, "enabled_input_methods", b.toString()); } catch (Throwable e) { note("Not allowed"); }
    }

    void buildBt() {
        if (bt == null) { info("Bluetooth", "Not available on this TV"); return; }
        final boolean on = bt.isEnabled();
        toggle("Bluetooth", on ? "On" : "Off", on, new Runnable() { public void run() {
            if (on) bt.disable(); else bt.enable();
            ui.postDelayed(new Runnable() { public void run() { refresh(); } }, 1200);
        } });
        if (!on) return;
        info("This TV appears as", bt.getName());
        header("Paired devices");
        Set<BluetoothDevice> bonded = bt.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) info("No paired devices", "");
        else for (final BluetoothDevice d : bonded) {
            Row r = row(ACTION, d.getName() == null ? d.getAddress() : d.getName(), d.getAddress(), null);
            r.act = new Runnable() { public void run() {
                choose(d.getName() == null ? d.getAddress() : d.getName(), new String[]{"Unpair"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface x, int w) {
                        try { d.getClass().getMethod("removeBond").invoke(d); } catch (Throwable e) { note("Could not unpair"); }
                        ui.postDelayed(new Runnable() { public void run() { refresh(); } }, 800);
                    }
                });
            } };
        }
        header(bt.isDiscovering() ? "Searching…" : "Available devices");
        for (final BluetoothDevice d : btFound) {
            Row r = row(ACTION, d.getName() == null ? d.getAddress() : d.getName(), "Select to pair", null);
            r.act = new Runnable() { public void run() { try { d.createBond(); } catch (Throwable e) { note("Could not pair"); } } };
        }
        Row sc = row(ACTION, bt.isDiscovering() ? "Stop searching" : "Search for devices", "Put the remote or headphones in pairing mode first", null);
        sc.act = new Runnable() { public void run() {
            try {
                if (bt.isDiscovering()) bt.cancelDiscovery();
                else { btFound.clear(); bt.startDiscovery(); }
            } catch (Throwable e) { note("Could not search"); }
            refresh();
        } };
    }

    void buildDisplay() {
        final boolean ss = sInt("screensaver_enabled", 1) == 1;
        toggle("Screensaver", "Show a slideshow when idle", ss, new Runnable() { public void run() { sPut("screensaver_enabled", ss ? 0 : 1); } });
        final int[] ssT = {60000, 300000, 900000, 1800000, 3600000};
        final String[] ssN = {"1 minute", "5 minutes", "15 minutes", "30 minutes", "1 hour"};
        int cur = yInt("screen_off_timeout", 300000);
        String cn = cur + " ms";
        for (int i = 0; i < ssT.length; i++) if (ssT[i] == cur) cn = ssN[i];
        Row a = row(ACTION, "Start screensaver after", "When nothing is being played", cn);
        a.act = new Runnable() { public void run() {
            choose("Start screensaver after", ssN, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { yPut("screen_off_timeout", ssT[w]); refresh(); }
            });
        } };
        final int[] sl = {-1, 3600000, 10800000, 21600000};
        final String[] slN = {"Never", "1 hour", "3 hours", "6 hours"};
        int sc = sInt("sleep_timeout", -1);
        String sn = sc + " ms";
        for (int i = 0; i < sl.length; i++) if (sl[i] == sc) sn = slN[i];
        Row b = row(ACTION, "Put TV to sleep after", "Turns the screen off after this long with no activity", sn);
        b.act = new Runnable() { public void run() {
            choose("Put TV to sleep after", slN, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { sPut("sleep_timeout", sl[w]); refresh(); }
            });
        } };
        header("HDMI");
        final boolean cec = gInt("hdmi_control_enabled", 0) == 1;
        toggle("HDMI control (CEC)", "Let devices on HDMI control this TV", cec, new Runnable() { public void run() { gPut("hdmi_control_enabled", cec ? 0 : 1); } });
        final boolean off = gInt("hdmi_control_auto_device_off_enabled", 0) == 1;
        toggle("Turn off devices with the TV", "Switch connected devices off too", off, new Runnable() { public void run() { gPut("hdmi_control_auto_device_off_enabled", off ? 0 : 1); } });
        final boolean wake = gInt("hdmi_control_auto_wakeup_enabled", 0) == 1;
        toggle("Wake the TV from devices", "Turn the TV on when a device starts", wake, new Runnable() { public void run() { gPut("hdmi_control_auto_wakeup_enabled", wake ? 0 : 1); } });
        header("Picture");
        Row p = row(ACTION, "Picture and sound modes", "Brightness, colour, game mode. Xiaomi's own panel page.", null);
        p.act = new Runnable() { public void run() { openSys(".device.display.DisplayActivity"); } };
    }

    void buildAccess() {
        final boolean cap = sInt("accessibility_captioning_enabled", 0) == 1;
        toggle("Captions", "Show subtitles when an app provides them", cap, new Runnable() { public void run() { sPut("accessibility_captioning_enabled", cap ? 0 : 1); } });
        final boolean hc = sInt("high_text_contrast_enabled", 0) == 1;
        toggle("High contrast text", "Make text easier to read", hc, new Runnable() { public void run() { sPut("high_text_contrast_enabled", hc ? 0 : 1); } });
        final boolean inv = sInt("accessibility_display_inversion_enabled", 0) == 1;
        toggle("Colour inversion", "Swap light and dark colours", inv, new Runnable() { public void run() { sPut("accessibility_display_inversion_enabled", inv ? 0 : 1); } });
        float fs = 1f;
        try { fs = Settings.System.getFloat(cr, "font_scale", 1f); } catch (Throwable ignored) { }
        final float[] sc = {0.85f, 1f, 1.15f, 1.3f};
        final String[] sn = {"Small", "Default", "Large", "Largest"};
        String cn = "Default";
        for (int i = 0; i < sc.length; i++) if (Math.abs(sc[i] - fs) < 0.02f) cn = sn[i];
        Row t = row(ACTION, "Text size", "Applies to apps that follow the system", cn);
        t.act = new Runnable() { public void run() {
            choose("Text size", sn, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try { Settings.System.putFloat(cr, "font_scale", sc[w]); } catch (Throwable e) { note("Needs permission"); }
                    refresh();
                }
            });
        } };
    }

    void openSys(String cls) {
        try { startActivity(new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings" + cls)); } catch (Throwable ignored) { }
    }

    // ---------- view ----------
    class V extends View {
        final float U;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF tmp = new RectF();
        final Typeface reg = Typeface.create("sans-serif", Typeface.NORMAL), med = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        int sel;
        boolean snap, positioned;
        float hy, hv, scroll, sv;
        long last;
        float[] ys = new float[0], hs = new float[0];

        V(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        }

        boolean selectable(int i) { int k = rows.get(i).kind; return k == ACTION || k == TOGGLE || k == ADJUST; }

        void fix() {
            int n = rows.size();
            ys = new float[n]; hs = new float[n];
            float y = 290 * U;
            for (int i = 0; i < n; i++) {
                int k = rows.get(i).kind;
                float h = (k == HEADER ? 76 : k == INFO ? 72 : 100) * U;
                ys[i] = y; hs[i] = h; y += h;
            }
            if (n == 0) { sel = 0; return; }
            sel = Math.max(0, Math.min(sel, n - 1));
            if (!selectable(sel)) { int a = sel; while (a < n && !selectable(a)) a++; if (a >= n) { a = sel; while (a >= 0 && !selectable(a)) a--; } sel = Math.max(0, a); }
        }

        float target() { return rows.isEmpty() ? 0 : Math.max(0, ys[sel] - 520 * U); }

        void move(int d) {
            int a = sel + d;
            while (a >= 0 && a < rows.size() && !selectable(a)) a += d;
            if (a >= 0 && a < rows.size()) sel = a;
        }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            if (rows.isEmpty()) return super.onKeyDown(k, e);
            Row r = rows.get(sel);
            if (k == KeyEvent.KEYCODE_DPAD_UP) move(-1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN) move(1);
            else if ((k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) && r.kind != ADJUST) {
                for (int n = 0; n < 8; n++) move(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            } else if ((k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) && r.kind == ADJUST && r.adj != null) {
                r.adj.by(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                refresh();
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (e.getRepeatCount() == 0 && r.act != null) { r.act.run(); if (r.kind == TOGGLE) refresh(); }
            } else return super.onKeyDown(k, e);
            invalidate();
            return true;
        }

        void bars(Canvas c, float x, float y, int level, int col) {
            int n = WifiManager.calculateSignalLevel(level, 4);
            for (int i = 0; i < 4; i++) {
                float h = (10 + i * 8) * U;
                p.setColor(i <= n ? col : (col & Th.c(0x00FFFFFF)) | Th.c(0x44000000));
                tmp.set(x + i * 12 * U, y + 32 * U - h, x + i * 12 * U + 8 * U, y + 32 * U);
                c.drawRoundRect(tmp, 3 * U, 3 * U, p);
            }
        }

        void lockIcon(Canvas c, float x, float y, int col) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(col);
            tmp.set(x, y + 12 * U, x + 22 * U, y + 30 * U);
            c.drawRoundRect(tmp, 3 * U, 3 * U, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3 * U);
            tmp.set(x + 3 * U, y, x + 19 * U, y + 20 * U);
            c.drawArc(tmp, 180, 180, false, p);
            p.setStyle(Paint.Style.FILL);
        }

        void sw(Canvas c, float x, float y, boolean on, int fg, boolean hl) {
            tmp.set(x, y, x + 84 * U, y + 44 * U);
            p.setStyle(Paint.Style.FILL);
            if (on) {
                p.setColor(hl ? Th.c(0xFF0F0F0F) : Th.W());
                c.drawRoundRect(tmp, 22 * U, 22 * U, p);
                p.setColor(hl ? Th.W() : Th.c(0xFF0F0F0F));
                c.drawCircle(x + 62 * U, y + 22 * U, 15 * U, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4 * U);
                p.setColor(hl ? Th.c(0xFF777777) : Th.c(0xFF909090));
                c.drawRoundRect(tmp, 22 * U, 22 * U, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(x + 22 * U, y + 22 * U, 12 * U, p);
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            c.drawColor(Th.c(0xFF0F0F0F));
            if (ys.length != rows.size()) fix();
            float tgtY = rows.isEmpty() ? 0 : ys[sel], tgtS = target(), h = 1f / 120f;
            if (snap) { snap = false; hy = tgtY; scroll = tgtS; hv = sv = 0; }
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
            c.drawText(title(), 96 * U, 190 * U, p);
            if (!wifiNote.isEmpty()) { p.setTextSize(28 * U); p.setColor(Th.c(0xFFAAAAAA)); c.drawText(wifiNote, 98 * U, 245 * U, p); }
            c.save();
            c.clipRect(0, 262 * U, getWidth(), getHeight());
            c.translate(0, -scroll);
            if (!rows.isEmpty()) {
                tmp.set(64 * U, hy - 6 * U, 1856 * U, hy + hs[sel] - 10 * U);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Th.W());
                c.drawRoundRect(tmp, 18 * U, 18 * U, p);
            }
            for (int i = 0; i < rows.size(); i++) {
                float y = ys[i];
                if (y - scroll < 200 * U || y - scroll > getHeight()) continue;
                Row r = rows.get(i);
                boolean on = i == sel;
                int fg = on ? Th.c(0xFF0F0F0F) : Th.c(0xFFDDDDDD);
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.LEFT);
                if (r.kind == HEADER) {
                    p.setTypeface(med);
                    p.setTextSize(28 * U);
                    p.setColor(Th.c(0xFF909090));
                    c.drawText(r.t.toUpperCase(), 112 * U, y + 52 * U, p);
                    continue;
                }
                if (r.kind == INFO) {
                    p.setTypeface(reg);
                    p.setTextSize(32 * U);
                    p.setColor(Th.c(0xFFAAAAAA));
                    c.drawText(r.t, 112 * U, y + 44 * U, p);
                    p.setColor(Th.c(0xFFFFFFFF));
                    p.setTextAlign(Paint.Align.RIGHT);
                    String v = r.v == null ? "" : r.v;
                    while (v.length() > 3 && p.measureText(v) > 1000 * U) v = v.substring(0, v.length() - 2);
                    c.drawText(v, 1832 * U, y + 44 * U, p);
                    continue;
                }
                p.setTypeface(med);
                p.setTextSize(36 * U);
                p.setColor(fg);
                String t = r.t;
                while (t.length() > 3 && p.measureText(t) > 1150 * U) t = t.substring(0, t.length() - 2);
                c.drawText(t, 112 * U, y + 38 * U, p);
                p.setTypeface(reg);
                p.setTextSize(26 * U);
                p.setColor(on ? Th.c(0xFF444444) : Th.c(0x88FFFFFF));
                String sub = r.kind == TOGGLE ? (r.s == null ? "" : r.s) : r.s;
                if (sub != null) {
                    while (sub.length() > 3 && p.measureText(sub) > 1250 * U) sub = sub.substring(0, sub.length() - 2);
                    c.drawText(sub, 112 * U, y + 72 * U, p);
                }
                if (r.kind == TOGGLE) sw(c, 1730 * U, y + 24 * U, r.on, fg, on);
                else {
                    if (r.level > -999) {
                        bars(c, 1760 * U, y + 18 * U, r.level, fg);
                        if (r.lock) lockIcon(c, 1700 * U, y + 24 * U, fg);
                    } else if (r.v != null) {
                        p.setTypeface(med);
                        p.setTextSize(34 * U);
                        p.setColor(fg);
                        p.setTextAlign(Paint.Align.RIGHT);
                        c.drawText(r.kind == ADJUST ? "‹  " + r.v + "  ›" : r.v, 1832 * U, y + 52 * U, p);
                    } else {
                        p.setTypeface(reg);
                        p.setTextSize(40 * U);
                        p.setColor(fg);
                        p.setTextAlign(Paint.Align.RIGHT);
                        c.drawText("›", 1832 * U, y + 52 * U, p);
                    }
                }
            }
            c.restore();
            if (run) postInvalidateOnAnimation();
        }
    }
}
