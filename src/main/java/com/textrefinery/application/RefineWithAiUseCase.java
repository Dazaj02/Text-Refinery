package com.textrefinery.application;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.AiProviderPort;
import com.textrefinery.domain.port.ClipboardPort;
import com.textrefinery.domain.port.MessagesPort;
import com.textrefinery.domain.port.NotifierPort;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Limpia el portapapeles y después lo refina con IA. Si la IA falla, queda solo la limpieza. */
public class RefineWithAiUseCase {

    private static final Logger log = LoggerFactory.getLogger(RefineWithAiUseCase.class);

    private final CleanClipboardUseCase cleaner;
    private final AiProviderPort ai;
    private final ClipboardPort clipboard;
    private final NotifierPort notifier;
    private final Supplier<Settings> settings;
    private final Executor executor;
    private final MessagesPort messages;

    public RefineWithAiUseCase(CleanClipboardUseCase cleaner, AiProviderPort ai, ClipboardPort clipboard,
                               NotifierPort notifier, Supplier<Settings> settings, Executor executor,
                               MessagesPort messages) {
        this.cleaner = cleaner;
        this.ai = ai;
        this.clipboard = clipboard;
        this.notifier = notifier;
        this.settings = settings;
        this.executor = executor;
        this.messages = messages;
    }

    public CompletableFuture<Void> execute(AiMode mode) {
        Optional<String> cleaned = cleaner.readAndClean();
        if (cleaned.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String text = cleaned.get();
        notifier.info(messages.get("notify.aiProcessing"));

        return CompletableFuture.supplyAsync(() -> ai.refine(text, mode), executor)
                .orTimeout(settings.get().aiTimeoutSeconds, TimeUnit.SECONDS)
                .handle((refined, error) -> {
                    boolean ok;
                    if (error == null && refined != null && !refined.isBlank()) {
                        String result = refined.strip();
                        ok = clipboard.writeText(result);
                        if (ok) {
                            cleaner.markWritten(result);
                            notifier.info(messages.get("notify.aiDone"));
                        }
                    } else {
                        // Degradación elegante: nunca se registra el contenido, solo el tipo de error.
                        log.warn("IA no disponible: {}", error == null ? "respuesta vacía" : error.getClass().getSimpleName());
                        ok = clipboard.writeText(text);
                        if (ok) {
                            cleaner.markWritten(text);
                            notifier.error(messages.get("notify.aiUnavailable"));
                        }
                    }
                    if (!ok) {
                        log.warn("No se pudo escribir en el portapapeles");
                        notifier.error(messages.get("notify.writeFailed"));
                    }
                    return null;
                });
    }
}
