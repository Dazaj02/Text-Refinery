package com.textrefinery.infrastructure.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Atajos globales con JNativeHook (Windows, macOS y Linux/X11).
 * macOS exige conceder permiso de Accesibilidad la primera vez.
 * Los callbacks se ejecutan en el hilo nativo: deben devolver rápido (App los delega a un executor).
 */
public class GlobalHotkeyManager implements NativeKeyListener {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalHotkeyManager.class);
    private static final long DEBOUNCE_MS = 400;

    private volatile Hotkey cleanKey;
    private volatile Hotkey aiKey;
    private final Runnable onClean;
    private final Runnable onAi;
    private volatile long lastFired;

    public GlobalHotkeyManager(Runnable onClean, Runnable onAi) {
        this.onClean = onClean;
        this.onAi = onAi;
    }

    /** Puede llamarse varias veces: solo cambia las combinaciones, el hook nativo se registra una vez. */
    public void configure(Hotkey clean, Hotkey ai) {
        this.cleanKey = clean;
        this.aiKey = ai;
    }

    public void start() throws NativeHookException {
        Logger jnh = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        jnh.setLevel(Level.WARNING);
        jnh.setUseParentHandlers(false);
        if (!GlobalScreen.isNativeHookRegistered()) {
            GlobalScreen.registerNativeHook();
        }
        GlobalScreen.addNativeKeyListener(this);
    }

    public void stop() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook();
            }
        } catch (NativeHookException e) {
            log.warn("No se pudo liberar el hook nativo: {}", e.getMessage());
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        long now = System.currentTimeMillis();
        if (now - lastFired < DEBOUNCE_MS) {
            return;
        }
        if (matches(cleanKey, e)) {
            lastFired = now;
            onClean.run();
        } else if (matches(aiKey, e)) {
            lastFired = now;
            onAi.run();
        }
    }

    private static boolean matches(Hotkey h, NativeKeyEvent e) {
        if (h == null) {
            return false;
        }
        int m = e.getModifiers();
        boolean mods = ((m & NativeKeyEvent.CTRL_MASK) != 0) == h.ctrl()
                && ((m & NativeKeyEvent.ALT_MASK) != 0) == h.alt()
                && ((m & NativeKeyEvent.SHIFT_MASK) != 0) == h.shift()
                && ((m & NativeKeyEvent.META_MASK) != 0) == h.meta();
        return mods && NativeKeyEvent.getKeyText(e.getKeyCode()).equalsIgnoreCase(h.key());
    }
}
