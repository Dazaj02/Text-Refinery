package com.textrefinery.application;

import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.ProcessingContext;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.ClipboardPort;
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

    public CleanClipboardUseCase(ClipboardPort clipboard, PipelineFactory pipelineFactory,
                                 NotifierPort notifier, Supplier<Settings> settings) {
        this.clipboard = clipboard;
        this.pipelineFactory = pipelineFactory;
        this.notifier = notifier;
        this.settings = settings;
    }

    /** Lee, limpia y vuelve a escribir en el portapapeles. */
    public void execute() {
        readAndClean().ifPresent(cleaned -> {
            if (clipboard.writeText(cleaned)) {
                notifier.info("Texto limpio ✓");
            } else {
                log.warn("No se pudo escribir en el portapapeles");
                notifier.error("No se pudo escribir en el portapapeles (otra aplicación lo tiene bloqueado)");
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
            notifier.error("El portapapeles no contiene texto");
            return Optional.empty();
        }
        if (raw.get().length() > s.maxClipboardChars) {
            log.warn("Contenido ignorado: {} caracteres superan el límite de {}", raw.get().length(), s.maxClipboardChars);
            notifier.error("El texto es demasiado largo (máx. " + s.maxClipboardChars + " caracteres)");
            return Optional.empty();
        }
        String cleaned = clean(raw.get(), s);
        log.info("Limpieza aplicada: {} → {} caracteres (perfil {})", raw.get().length(), cleaned.length(), s.defaultProfile);
        return Optional.of(cleaned);
    }

    /** Lógica pura, sin portapapeles: útil para pruebas y para la vista previa de ajustes. */
    public String clean(String text, Settings s) {
        var pipeline = pipelineFactory.build(s.defaultProfile, s.disabledProcessors);
        return pipeline.process(text, ProcessingContext.of(s.defaultProfile));
    }
}
