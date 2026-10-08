package com.textrefinery.application;

import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.ProcessingContext;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.ClipboardPort;
import com.textrefinery.domain.port.MessagesPort;
import com.textrefinery.domain.port.NotifierPort;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Limpieza rápida y determinista del portapapeles. No depende de la IA. */
public class CleanClipboardUseCase {

    private static final Logger log = LoggerFactory.getLogger(CleanClipboardUseCase.class);

    private final ClipboardPort clipboard;
    private final PipelineFactory pipelineFactory;
    private final NotifierPort notifier;
    private final Supplier<Settings> settings;
    private final MessagesPort messages;
    // Solo en memoria (privacidad): se pierden al cerrar la app. Se accede desde hilos distintos.
    private volatile String lastOriginal;
    private volatile String lastWritten;

    public CleanClipboardUseCase(ClipboardPort clipboard, PipelineFactory pipelineFactory,
                                 NotifierPort notifier, Supplier<Settings> settings, MessagesPort messages) {
        this.clipboard = clipboard;
        this.pipelineFactory = pipelineFactory;
        this.notifier = notifier;
        this.settings = settings;
        this.messages = messages;
    }

    /** Lee, limpia y vuelve a escribir en el portapapeles. */
    public void execute() {
        readAndClean().ifPresent(cleaned -> {
            if (clipboard.writeText(cleaned)) {
                markWritten(cleaned);
                notifier.info(messages.get("notify.cleaned"));
            } else {
                log.warn("No se pudo escribir en el portapapeles");
                notifier.error(messages.get("notify.writeFailed"));
            }
        });
    }

    /** Lee el portapapeles y devuelve el texto limpio sin escribirlo; avisa al usuario si no procede. */
    public Optional<String> readAndClean() {
        Settings s = settings.get();
        Optional<String> raw = Optional.<String>empty();
        if (s.defaultProfile == CleaningProfile.MARKDOWN) {
            raw = clipboard.readHtml();
        }
        raw = raw.or(clipboard::readText);

        if (raw.isEmpty() || raw.get().isBlank()) {
            notifier.error(messages.get("notify.emptyClipboard"));
            return Optional.empty();
        }
        if (raw.get().length() > s.maxClipboardChars) {
            log.warn("Contenido ignorado: {} caracteres superan el límite de {}", raw.get().length(), s.maxClipboardChars);
            notifier.error(messages.get("notify.tooLong", s.maxClipboardChars));
            return Optional.empty();
        }
        rememberOriginal(raw.get(), s);
        String cleaned = clean(raw.get(), s);
        log.info("Limpieza aplicada: {} → {} caracteres (perfil {})", raw.get().length(), cleaned.length(), s.defaultProfile);
        return Optional.of(cleaned);
    }

    /**
     * Guarda en memoria el texto plano que había antes de limpiar, para poder deshacer. Nunca se escribe en disco ni en logs.
     * Si el portapapeles ya contiene lo último que escribimos (se pulsó el atajo dos veces) se conserva el original verdadero.
     */
    private void rememberOriginal(String raw, Settings s) {
        // En el perfil Markdown `raw` puede ser HTML; lo que se restaura debe ser el texto plano.
        String plain = s.defaultProfile == CleaningProfile.MARKDOWN ? clipboard.readText().orElse(raw) : raw;
        if (!plain.equals(lastWritten)) {
            lastOriginal = plain;
        }
    }

    /** Avisa de lo que se escribió en el portapapeles, para no confundirlo con un original nuevo. */
    public void markWritten(String text) {
        lastWritten = text;
    }

    /** Devuelve al portapapeles el texto que había antes de la última limpieza. */
    public void restoreOriginal() {
        String original = lastOriginal;
        if (original == null) {
            notifier.error(messages.get("notify.nothingToRestore"));
            return;
        }
        if (clipboard.writeText(original)) {
            lastWritten = original;
            notifier.info(messages.get("notify.restored"));
        } else {
            notifier.error(messages.get("notify.writeFailed"));
        }
    }

    /** Lógica pura, sin portapapeles: útil para pruebas y para la vista previa de ajustes. */
    public String clean(String text, Settings s) {
        var pipeline = pipelineFactory.build(s.defaultProfile, s.disabledProcessors);
        return pipeline.process(text, ProcessingContext.of(s.defaultProfile));
    }
}
