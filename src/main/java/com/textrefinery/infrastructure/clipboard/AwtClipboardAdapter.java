package com.textrefinery.infrastructure.clipboard;

import com.textrefinery.domain.port.ClipboardPort;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.Reader;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Portapapeles del sistema vía AWT. Reintenta porque en Windows puede estar bloqueado por otra app. */
public class AwtClipboardAdapter implements ClipboardPort {

    private static final Logger log = LoggerFactory.getLogger(AwtClipboardAdapter.class);
    private static final int ATTEMPTS = 10;
    private static final long RETRY_DELAY_MS = 100;

    private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    @Override
    public Optional<String> readText() {
        return read(DataFlavor.stringFlavor);
    }

    @Override
    public Optional<String> readHtml() {
        return read(DataFlavor.fragmentHtmlFlavor);
    }

    @Override
    public boolean writeText(String text) {
        StringSelection selection = new StringSelection(text);
        // Optional.ofNullable(TRUE) distingue éxito de "agotados los reintentos".
        return retry(() -> {
            clipboard.setContents(selection, selection);
            return Boolean.TRUE;
        }).isPresent();
    }

    private Optional<String> read(DataFlavor flavor) {
        return retry(() -> {
            if (!clipboard.isDataFlavorAvailable(flavor)) {
                return Optional.<String>empty();
            }
            Object data = clipboard.getData(flavor);
            if (data instanceof String s) {
                return Optional.of(s);
            }
            if (data instanceof Reader r) {
                try (r) {
                    java.io.StringWriter out = new java.io.StringWriter();
                    r.transferTo(out);
                    return Optional.of(out.toString());
                }
            }
            return Optional.<String>empty();
        }).flatMap(o -> o);
    }

    private <T> Optional<T> retry(ThrowingSupplier<T> action) {
        for (int i = 1; i <= ATTEMPTS; i++) {
            try {
                return Optional.ofNullable(action.get());
            } catch (Exception e) {
                // No se registra el contenido, solo el intento fallido.
                log.debug("Portapapeles no disponible (intento {}/{}): {}", i, ATTEMPTS, e.getClass().getSimpleName());
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
