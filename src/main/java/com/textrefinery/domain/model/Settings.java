package com.textrefinery.domain.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuración del usuario (se serializa a settings.json).
 * Campos públicos con valores por defecto: un JSON incompleto sigue siendo válido.
 */
public class Settings {
    public CleaningProfile defaultProfile = CleaningProfile.WORD;
    // Sin Alt: en Windows Ctrl+Alt equivale a AltGr y puede activar la cinta de Office; Ctrl+Alt+V es "Pegado especial" en Word.
    public String cleanHotkey = "Ctrl+Shift+Y";
    public String aiHotkey = "Ctrl+Shift+G";
    public AiMode aiMode = AiMode.GRAMMAR;
    public String ollamaUrl = "http://localhost:11434";
    public String aiModel = "llama3.2";
    public int aiTimeoutSeconds = 60;
    public int maxClipboardChars = 200_000;
    public Set<String> disabledProcessors = new HashSet<>();
    /** Preferencia del usuario; el estado real del arranque lo guarda el sistema operativo (ver AutostartPort). */
    public boolean launchAtLogin = false;

    public Settings copy() {
        Settings c = new Settings();
        c.defaultProfile = defaultProfile;
        c.cleanHotkey = cleanHotkey;
        c.aiHotkey = aiHotkey;
        c.aiMode = aiMode;
        c.ollamaUrl = ollamaUrl;
        c.aiModel = aiModel;
        c.aiTimeoutSeconds = aiTimeoutSeconds;
        c.maxClipboardChars = maxClipboardChars;
        c.disabledProcessors = new HashSet<>(disabledProcessors);
        c.launchAtLogin = launchAtLogin;
        return c;
    }
}
