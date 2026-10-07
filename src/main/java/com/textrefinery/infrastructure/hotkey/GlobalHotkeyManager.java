package com.textrefinery.infrastructure.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
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
    private static final Field RESERVED = findReservedField();

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
        GlobalScreen.setEventDispatcher(new DirectExecutor());
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
        boolean clean = matches(cleanKey, e);
        boolean ai = !clean && matches(aiKey, e);
        if (!clean && !ai) {
            return;
        }
        // La tecla es nuestra: la app en primer plano (p. ej. Word) no debe recibirla.
        consume(e);
        long now = System.currentTimeMillis();
        if (now - lastFired < DEBOUNCE_MS) {
            return;
        }
        lastFired = now;
        (clean ? onClean : onAi).run();
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        if (matches(cleanKey, e) || matches(aiKey, e)) {
            consume(e);
        }
    }

    /** Suprime el evento para el resto del sistema (funciona en Windows y macOS; en Linux/X11 es best-effort). */
    private static void consume(NativeKeyEvent e) {
        if (RESERVED == null) {
            return;
        }
        try {
            RESERVED.setShort(e, (short) 0x01);
        } catch (IllegalAccessException ex) {
            log.debug("No se pudo consumir el evento de teclado");
        }
    }

    private static Field findReservedField() {
        try {
            Field f = NativeInputEvent.class.getDeclaredField("reserved");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            log.warn("El atajo no podrá ocultarse a otras aplicaciones: {}", ex.getClass().getSimpleName());
            return null;
        }
    }

    /** Ejecuta en el mismo hilo nativo: el hook solo puede suprimir la tecla si el listener termina antes de volver. */
    private static final class DirectExecutor extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
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
