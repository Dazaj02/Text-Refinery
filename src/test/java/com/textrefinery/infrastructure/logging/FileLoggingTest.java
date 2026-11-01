package com.textrefinery.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLoggingTest {

    @Test
    void duplicaLaSalidaDeErroresEnElArchivoYEnLaConsola(@TempDir Path dir) throws Exception {
        PrintStream original = System.err;
        ByteArrayOutputStream console = new ByteArrayOutputStream();
        System.setErr(new PrintStream(console, true, StandardCharsets.UTF_8));
        try {
            Path log = dir.resolve("sub").resolve("app.log");
            FileLogging.install(log);
            System.err.println("línea de prueba ñ");
            System.err.flush();

            assertThat(Files.readString(log)).contains("línea de prueba ñ");
            assertThat(console.toString(StandardCharsets.UTF_8)).contains("línea de prueba ñ");
            assertThat(System.getProperty("org.slf4j.simpleLogger.showDateTime")).isEqualTo("true");
        } finally {
            System.setErr(original);
        }
    }

    @Test
    void siLaConsolaFallaElArchivoSigueRecibiendo(@TempDir Path dir) throws Exception {
        PrintStream original = System.err;
        System.setErr(new PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                throw new java.io.IOException("sin consola");
            }
        }, true));
        try {
            Path log = dir.resolve("app.log");
            FileLogging.install(log);
            System.err.println("sigue vivo");
            System.err.flush();
            assertThat(Files.readString(log)).contains("sigue vivo");
        } finally {
            System.setErr(original);
        }
    }

    @Test
    void rotaElArchivoCuandoSuperaElLimite(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("app.log");
        Files.writeString(log, "x".repeat(200));
        FileLogging.rotateIfNeeded(log, 100);
        assertThat(log).doesNotExist();
        assertThat(dir.resolve("app.log.1")).exists();

        Files.writeString(log, "pequeño");
        FileLogging.rotateIfNeeded(log, 100);
        assertThat(log).exists();
    }

    @Test
    void sinDirectorioEscribibleNoLanzaExcepcion(@TempDir Path dir) throws Exception {
        Path blocker = Files.writeString(dir.resolve("archivo"), "no soy un directorio");
        FileLogging.install(blocker.resolve("app.log")); // el padre es un archivo: debe ignorarse sin romper
    }
}
