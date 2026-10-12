package com.textrefinery.domain.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuración del usuario (se serializa a settings.json).
 * Campos públicos con valores por defecto: un JSON incompleto sigue siendo válido.
 */
public class Settings {
    // Sin Alt: en Windows Ctrl+Alt equivale a AltGr y puede activar la cinta de Office; Ctrl+Alt+V es "Pegado especial" en Word.
    /** Atajo predeterminado de limpieza; el botón «Restablecer» de los ajustes vuelve a él. */
    public static final String DEFAULT_CLEAN_HOTKEY = "Ctrl+Shift+Y";
    /** Atajo predeterminado de limpieza con IA. */
    public static final String DEFAULT_AI_HOTKEY = "Ctrl+Shift+G";

    public CleaningProfile defaultProfile = CleaningProfile.WORD;
    public String cleanHotkey = DEFAULT_CLEAN_HOTKEY;
    public String aiHotkey = DEFAULT_AI_HOTKEY;
    public AiMode aiMode = AiMode.GRAMMAR;
    public String ollamaUrl = "http://localhost:11434";
    public String aiModel = "llama3.2";
    public int aiTimeoutSeconds = 60;
    /** Si Ollama es local y no está en marcha, la app lo arranca al usar la IA (y lo cierra al salir). */
    public boolean autoStartOllama = true;
    public int maxClipboardChars = 200_000;
    public Set<String> disabledProcessors = new HashSet<>();
    /** Preferencia del usuario; el estado real del arranque lo guarda el sistema operativo (ver AutostartPort). */
    public boolean launchAtLogin = false;
    public ThemeMode theme = ThemeMode.LIGHT;
    /** Idioma en el que se pide el trabajo a la IA. */
    public Language textLanguage = Language.ES;
    /** Idioma de la interfaz; si el JSON no lo trae se toma del sistema. */
    public Language appLanguage = Language.fromLocale(java.util.Locale.getDefault());

    public Settings copy() {
        Settings c = new Settings();
        c.defaultProfile = defaultProfile;
        c.cleanHotkey = cleanHotkey;
        c.aiHotkey = aiHotkey;
        c.aiMode = aiMode;
        c.ollamaUrl = ollamaUrl;
        c.aiModel = aiModel;
        c.aiTimeoutSeconds = aiTimeoutSeconds;
        c.autoStartOllama = autoStartOllama;
        c.maxClipboardChars = maxClipboardChars;
        c.disabledProcessors = new HashSet<>(disabledProcessors);
        c.launchAtLogin = launchAtLogin;
        c.theme = theme;
        c.textLanguage = textLanguage;
        c.appLanguage = appLanguage;
        return c;
    }
}
