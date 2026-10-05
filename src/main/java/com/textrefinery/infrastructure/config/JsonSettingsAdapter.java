package com.textrefinery.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.SettingsPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persiste la configuración en settings.json dentro del directorio del usuario. */
public class JsonSettingsAdapter implements SettingsPort {

    private static final Logger log = LoggerFactory.getLogger(JsonSettingsAdapter.class);

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public JsonSettingsAdapter(Path file) {
        this.file = file;
    }

    @Override
    public Settings load() {
        try {
            if (Files.exists(file)) {
                return mapper.readValue(file.toFile(), Settings.class);
            }
            Settings defaults = loadBundledDefaults();
            save(defaults);
            return defaults;
        } catch (IOException | RuntimeException e) {
            log.warn("Configuración ilegible, se usan los valores por defecto: {}", e.getMessage());
            return new Settings();
        }
    }

    @Override
    public void save(Settings settings) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), settings);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la configuración", e);
        }
    }

    private Settings loadBundledDefaults() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/default-settings.json")) {
            return in == null ? new Settings() : mapper.readValue(in, Settings.class);
        }
    }
}
