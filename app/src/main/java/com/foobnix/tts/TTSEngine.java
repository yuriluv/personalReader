package com.foobnix.tts;

public class TTSEngine {
    private static final TTSEngine instance = new TTSEngine();
    public static final String WAV = ".wav";
    public static final String MP3 = ".mp3";

    public static TTSEngine get() {
        return instance;
    }

    public void shutdown() {}
    public void stop() {}
    public void stop(Object tts) {}
    public boolean isShutdown() { return true; }
    public boolean isTempPausing() { return false; }
    public boolean isPlaying() { return false; }
    public void fastTTSBookmakr(Object dc) {}
    public void mp3Destroy() {}
    public boolean hasNoEngines() { return true; }
    public void getTTS() {}
    public void getTTS(Object listener) {}
    public String getCurrentEngineName() { return ""; }
    public String getCurrentLang() { return ""; }
    public void speakToFile(Object... args) {}
    public void speek(String text) {}
}
