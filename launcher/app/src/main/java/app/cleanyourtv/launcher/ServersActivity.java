package app.cleanyourtv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.List;

public class ServersActivity extends Activity {
    private List<Servers.S> list;
    private V view;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Th.refresh(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Th.K()));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        list = Servers.load(this);
        view = new V(this);
        setContentView(view);
    }

    void changed() {
        Servers.save(this, list);
        view.sel = Math.max(0, Math.min(view.sel, list.size()));
        view.invalidate();
    }

    void actions(final int i) {
        final Servers.S s = list.get(i);
        String[] items = {s.on ? "Hide from Home" : "Show on Home", "Edit name or address", "Move up", "Move down", "Delete"};
        new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle(s.name).setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                if (which == 0) { s.on = !s.on; changed(); }
                else if (which == 1) edit(i);
                else if (which == 2 && i > 0) { list.remove(i); list.add(i - 1, s); view.sel = i - 1; changed(); }
                else if (which == 3 && i < list.size() - 1) { list.remove(i); list.add(i + 1, s); view.sel = i + 1; changed(); }
                else if (which == 4) { list.remove(i); changed(); }
            }
        }).show();
    }

    void edit(final int i) {
        final Servers.S s = i >= 0 ? list.get(i) : null;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText n = new EditText(this), u = new EditText(this);
        n.setHint("Name, e.g. Movies - Home");
        u.setHint("Address, e.g. https://server3.ftpbd.net/FTP-3/");
        n.setSingleLine(true);
        u.setSingleLine(true);
        u.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (s != null) { n.setText(s.name); u.setText(s.url); } else u.setText("https://");
        box.addView(n);
        box.addView(u);
        new AlertDialog.Builder(this, R.style.Dark_Dialog).setTitle(s == null ? "Add server" : "Edit server").setView(box)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String name = Servers.clean(n.getText().toString()), url = Servers.clean(u.getText().toString());
                        if (name.isEmpty() || url.isEmpty() || url.equals("https://")) return;
                        Servers.S x = s != null ? s : new Servers.S();
                        x.name = name;
                        x.url = Servers.normUrl(url);
                        if (s == null) { list.add(x); view.sel = list.size() - 1; }
                        changed();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    class V extends View {
        final float U;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF tmp = new RectF();
        final Typeface thin, light, reg;
        int sel;
        float hy, hv, scroll, sv;
        long last;

        V(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
            thin = font(200);
            light = font(300);
            reg = font(400);
            hy = rowY(0);
        }

        Typeface font(int w) {
            try {
                return Typeface.create(w >= 400 ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL);
            } catch (Throwable t) {
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            }
        }

        float rowH() { return 118 * U; }
        float rowY(int i) { return 300 * U + i * rowH(); }
        float target() { return Math.max(0, rowY(sel) - 560 * U); }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            int n = list.size() + 1;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP: sel = sel > 0 ? sel - 1 : n - 1; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: sel = sel < n - 1 ? sel + 1 : 0; break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() > 0) return true;
                    if (sel == list.size()) edit(-1); else actions(sel);
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
            c.drawColor(Th.c(0xFF0F0F0F));
            float tgtY = rowY(sel), tgtS = target();
            float h = 1f / 120f;
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
            c.drawText("Servers", 96 * U, 190 * U, p);
            p.setTypeface(light);
            p.setTextSize(30 * U);
            p.setColor(Th.c(0x99FFFFFF));
            c.drawText("Choose a server to hide, edit or move it. Changes show on Home straight away.", 98 * U, 250 * U, p);

            c.save();
            c.clipRect(0, 270 * U, getWidth(), getHeight());
            c.translate(0, -scroll);
            tmp.set(64 * U, hy - 6 * U, 1856 * U, hy + rowH() - 14 * U);
            p.setColor(Th.W());
            c.drawRoundRect(tmp, 18 * U, 18 * U, p);
            p.setShader(null);
            for (int i = 0; i <= list.size(); i++) {
                float y = rowY(i);
                boolean on = i == sel;
                p.setTextAlign(Paint.Align.LEFT);
                if (i == list.size()) {
                    p.setTypeface(reg);
                    p.setTextSize(38 * U);
                    p.setColor(on ? Th.c(0xFF0F0F0F) : Th.c(0xFFAAAAAA));
                    c.drawText("+  Add a server", 112 * U, y + 62 * U, p);
                    continue;
                }
                Servers.S s = list.get(i);
                p.setTypeface(reg);
                p.setTextSize(38 * U);
                p.setColor(on ? Th.c(0xFF0F0F0F) : Th.c(0xFFAAAAAA));
                if (!s.on) p.setAlpha(on ? 170 : 110);
                c.drawText(s.name, 112 * U, y + 50 * U, p);
                p.setTypeface(light);
                p.setTextSize(26 * U);
                p.setColor(on ? Th.c(0xFF444444) : Th.c(0x88FFFFFF));
                String u = s.url;
                while (u.length() > 3 && p.measureText(u) > 1300 * U) u = u.substring(0, u.length() - 2);
                c.drawText(u, 112 * U, y + 88 * U, p);
                p.setTextAlign(Paint.Align.RIGHT);
                p.setTypeface(reg);
                p.setTextSize(30 * U);
                p.setColor(on ? 0xFF0F0F0F : (s.on ? 0xFF7CE0A0 : 0x88FFFFFF));
                c.drawText(s.on ? "On Home" : "Hidden", 1832 * U, y + 60 * U, p);
            }
            c.restore();
            if (run) postInvalidateOnAnimation();
        }
    }
}
