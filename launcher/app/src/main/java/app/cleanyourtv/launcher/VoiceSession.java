package app.cleanyourtv.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

public class VoiceSession extends VoiceInteractionSession {
    static final String KATNISS = "com.google.android.katniss";
    static final String REC = "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService";

    private Orb orb;
    private SpeechRecognizer sr;

    public VoiceSession(Context c) {
        super(c);
    }

    @Override
    public View onCreateContentView() {
        orb = new Orb(getContext());
        orb.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return orb;
    }

    @Override
    public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        orb.reset();
        listen();
    }

    @Override
    public void onHide() {
        super.onHide();
        stop();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        stop();
        hide();
    }

    void stop() {
        if (sr != null) {
            try { sr.cancel(); sr.destroy(); } catch (Throwable ignored) { }
            sr = null;
        }
        if (orb != null) orb.mode = Orb.IDLE;
    }

    void listen() {
        stop();
        try {
            sr = SpeechRecognizer.createSpeechRecognizer(getContext(), new ComponentName(KATNISS, REC));
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p) { orb.mode = Orb.LISTEN; orb.text = ""; orb.invalidate(); }
                public void onBeginningOfSpeech() { }
                public void onRmsChanged(float db) { orb.level = Math.max(0f, Math.min(1f, (db + 2f) / 12f)); orb.invalidate(); }
                public void onBufferReceived(byte[] b) { }
                public void onEndOfSpeech() { orb.mode = Orb.THINK; orb.invalidate(); }
                public void onError(int e) {
                    orb.mode = Orb.ERROR;
                    orb.text = e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ? "I didn't catch that" : "Voice isn't available right now";
                    orb.invalidate();
                }
                public void onResults(Bundle r) {
                    ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l == null || l.isEmpty()) { onError(SpeechRecognizer.ERROR_NO_MATCH); return; }
                    final String q = l.get(0);
                    orb.text = q;
                    orb.mode = Orb.THINK;
                    orb.invalidate();
                    orb.postDelayed(new Runnable() { public void run() { go(q); } }, 350);
                }
                public void onPartialResults(Bundle r) {
                    ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l != null && !l.isEmpty()) { orb.text = l.get(0); orb.invalidate(); }
                }
                public void onEvent(int t, Bundle b) { }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            orb.mode = Orb.THINK;
            sr.startListening(i);
        } catch (Throwable t) {
            orb.mode = Orb.ERROR;
            orb.text = "Voice isn't available right now";
            orb.invalidate();
        }
    }

    void go(String q) {
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH").setPackage(KATNISS)
                .putExtra("query", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startAssistantActivity(i); } catch (Throwable t) {
            try { getContext().startActivity(i); } catch (Throwable ignored) { }
        }
        stop();
        hide();
    }

    class Orb extends View {
        static final int IDLE = 0, LISTEN = 1, THINK = 2, ERROR = 3;
        int mode = THINK;
        float level, shown, phase;
        String text = "";
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final float U;
        long last;

        Orb(Context c) {
            super(c);
            U = c.getResources().getDisplayMetrics().widthPixels / 1920f;
            setFocusable(true);
            setFocusableInTouchMode(true);
        }

        void reset() { mode = THINK; text = ""; level = shown = 0; requestFocus(); invalidate(); }

        @Override
        public boolean onKeyDown(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                if (e.getRepeatCount() == 0) listen();
                return true;
            }
            return super.onKeyDown(k, e);
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = System.nanoTime();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            phase += dt;
            shown += ((mode == LISTEN ? level : 0f) - shown) * Math.min(1f, dt * 10f);
            c.drawColor(0xFF0F0F0F);
            float cx = getWidth() / 2f, cy = getHeight() * 0.42f;
            float base = 120 * U;
            float breathe = mode == ERROR ? 0f : (float) Math.sin(phase * (mode == THINK ? 3.2 : 2.0)) * 0.04f;
            float rad = base * (1f + breathe + shown * 0.45f);
            int a = mode == ERROR ? 40 : 255;
            for (int i = 3; i >= 1; i--) {
                float rr = rad * (1f + i * 0.55f * (0.5f + shown));
                p.setShader(new RadialGradient(cx, cy, rr, Color.argb((int) (a * 0.20f / i), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rr, p);
            }
            p.setShader(null);
            p.setColor(Color.argb(a, 255, 255, 255));
            c.drawCircle(cx, cy, rad, p);
            if (mode == THINK) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6 * U);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0x66FFFFFF);
                float rr = rad + 46 * U;
                r.set(cx - rr, cy - rr, cx + rr, cy + rr);
                c.drawArc(r, phase * 260f, 90f, false, p);
                p.setStyle(Paint.Style.FILL);
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setColor(Color.WHITE);
            p.setTextSize(64 * U);
            String t = text;
            while (t.length() > 3 && p.measureText(t) > 1500 * U) t = t.substring(t.indexOf(' ') > 0 ? t.indexOf(' ') + 1 : 1);
            String line = !text.isEmpty() ? t : (mode == LISTEN ? "Listening…" : mode == ERROR ? "" : "");
            if (mode == ERROR) { p.setColor(0xFFFFFFFF); line = text; }
            if (!text.isEmpty() || mode == LISTEN || mode == ERROR) c.drawText(line, cx, getHeight() * 0.72f, p);
            p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            p.setTextSize(30 * U);
            p.setColor(0x99FFFFFF);
            String hint = mode == ERROR ? "Press OK to try again   ·   Back to close" : "Back to close";
            c.drawText(hint, cx, getHeight() * 0.72f + 70 * U, p);
            postInvalidateOnAnimation();
        }
    }
}
