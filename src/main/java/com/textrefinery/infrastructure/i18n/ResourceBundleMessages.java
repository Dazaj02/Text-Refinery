package com.textrefinery.infrastructure.i18n;

import com.textrefinery.domain.model.Language;
import com.textrefinery.domain.port.MessagesPort;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mensajes desde {@code i18n/messages_<idioma>.properties} (UTF-8). El idioma puede cambiarse en caliente:
 * el siguiente {@link #get} ya usa el nuevo, sin reiniciar.
 */
public final class ResourceBundleMessages implements MessagesPort {

    private static final Logger log = LoggerFactory.getLogger(ResourceBundleMessages.class);
    static final String BASE_NAME = "i18n/messages";

    private volatile Language language;
    private volatile ResourceBundle bundle;

    public ResourceBundleMessages(Language language) {
        setLanguage(language);
    }

    public void setLanguage(Language language) {
        // Sin fallback al idioma del sistema: si falta una clave en inglés no debe aparecer en español "por sorpresa".
        this.bundle = ResourceBundle.getBundle(BASE_NAME, Locale.forLanguageTag(language.code()),
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        this.language = language;
    }

    @Override
    public Language language() {
        return language;
    }

    @Override
    public String get(String key, Object... args) {
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException e) {
            log.warn("Falta el mensaje '{}' en el idioma {}", key, language);
            return "!" + key + "!";
        }
        // Sustitución simple en vez de MessageFormat: este trata las comillas simples como escape y rompería "don't".
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }
}
