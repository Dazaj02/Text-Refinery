package com.textrefinery.infrastructure.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Duplica la salida de errores (donde escribe SLF4J Simple) en un archivo, para que haya registro aunque la app
 * se abra con doble clic y no tenga consola. Debe instalarse antes de crear el primer logger.
 * Nunca se escribe el contenido del portapapeles: ese es un requisito del proyecto, no de esta clase.
 */
public final class FileLogging {

    public static final long DEFAULT_MAX_BYTES = 1_000_000;

    private FileLogging() {
    }

    /** Configura formato con fecha y hora y empieza a duplicar System.err en {@code file}. No lanza: sin archivo, solo consola. */
    public static void install(Path file) {
        install(file, DEFAULT_MAX_BYTES);
    }

    static void install(Path file, long maxBytes) {
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss");
        try {
            Files.createDirectories(file.getParent());
            rotateIfNeeded(file, maxBytes);
            OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.setErr(new TeePrintStream(System.err, new PrintStream(out, true, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            // Sin archivo de registro la app funciona igual; el error ya no se puede registrar en ningún sitio.
        }
    }

    /** Conserva una copia anterior (app.log.1) cuando el archivo supera el límite. */
    static void rotateIfNeeded(Path file, long maxBytes) throws IOException {
        if (Files.exists(file) && Files.size(file) > maxBytes) {
            Files.move(file, file.resolveSibling(file.getFileName() + ".1"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reparte el TEXTO entre dos flujos y deja que cada uno lo codifique a su manera: la consola de Windows no es UTF-8,
     * el archivo sí. (Duplicar bytes ya codificados rompería los acentos en uno de los dos.)
     * PrintStream no lanza excepciones de E/S, así que si un destino falla el otro sigue funcionando.
     */
    private static final class TeePrintStream extends PrintStream {
        private final PrintStream console;
        private final PrintStream file;

        TeePrintStream(PrintStream console, PrintStream file) {
            super(OutputStream.nullOutputStream(), true);
            this.console = console;
            this.file = file;
        }

        @Override
        public void print(String s) {
            console.print(s);
            file.print(s);
        }

        @Override
        public void println(String s) {
            console.println(s);
            file.println(s);
        }

        @Override
        public void print(Object o) {
            print(String.valueOf(o));
        }

        @Override
        public void println(Object o) {
            println(String.valueOf(o));
        }

        @Override
        public void print(char[] s) {
            print(new String(s));
        }

        @Override
        public void println(char[] s) {
            println(new String(s));
        }

        @Override
        public void println() {
            console.println();
            file.println();
        }

        @Override
        public void write(int b) {
            console.write(b);
            file.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            console.write(buf, off, len);
            file.write(buf, off, len);
        }

        @Override
        public void flush() {
            console.flush();
            file.flush();
        }
    }
}
