package app.cleanyourtv.launcher;

import android.content.Intent;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

public class StubRecognizer extends RecognitionService {
    @Override protected void onStartListening(Intent i, Callback c) {
        try { c.error(SpeechRecognizer.ERROR_CLIENT); } catch (Throwable ignored) { }
    }
    @Override protected void onCancel(Callback c) { }
    @Override protected void onStopListening(Callback c) { }
}
