package com.textrefinery.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.AiProviderException;
import com.textrefinery.domain.port.AiProviderPort;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/** Adaptador para un servidor Ollama local (/api/generate, sin streaming). */
public class OllamaAiProvider implements AiProviderPort {

    /** Margen para que un Ollama recién lanzado empiece a atender. */
    private static final int START_WAIT_SECONDS = 25;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Supplier<Settings> settings;
    private final OllamaLauncher launcher;

    /** Sin lanzador: solo se conecta a un Ollama que ya esté en marcha. */
    public OllamaAiProvider(Supplier<Settings> settings) {
        this(settings, null);
    }

    public OllamaAiProvider(Supplier<Settings> settings, OllamaLauncher launcher) {
        this.settings = settings;
        this.launcher = launcher;
    }

    @Override
    public String refine(String text, AiMode mode) {
        Settings s = settings.get();
        if (launcher != null) {
            ensureAvailable(s);
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", s.aiModel,
                    "prompt", PromptTemplates.render(mode, s.textLanguage, text),
                    "stream", false,
                    // Temperatura baja: son tareas de fidelidad (corregir, resumir, citar), no de creatividad.
                    // num_ctx: el predeterminado de Ollama (4096) recorta en silencio los textos largos.
                    "options", Map.of("temperature", 0.2, "num_ctx", 8192)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(base(s) + "/api/generate"))
                    .timeout(Duration.ofSeconds(s.aiTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AiProviderException("Ollama respondió HTTP " + response.statusCode());
            }
            JsonNode json = mapper.readTree(response.body());
            return stripDelimiters(json.path("response").asText(""));
        } catch (IOException e) {
            throw new AiProviderException("No se pudo contactar con Ollama", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Llamada a Ollama interrumpida", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return isAvailable(settings.get());
    }

    /**
     * Comprueba la conexión y, si no responde, Ollama es local y el ajuste está activo, lo arranca y espera a que
     * conteste. Con el ajuste apagado (o sin lanzador) solo comprueba.
     */
    public boolean ensureAvailable(Settings s) {
        if (isAvailable(s)) {
            return true;
        }
        if (launcher == null || !s.autoStartOllama || !OllamaLauncher.isLocal(s.ollamaUrl) || !launcher.start()) {
            return false;
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(START_WAIT_SECONDS).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isAvailable(s)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAvailable(Settings s) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base(s) + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Los modelos pequeños a veces devuelven el texto envuelto en las etiquetas del prompt; no deben llegar al portapapeles. */
    static String stripDelimiters(String response) {
        String r = response.strip();
        if (r.startsWith("<texto>")) {
            r = r.substring("<texto>".length());
        }
        if (r.endsWith("</texto>")) {
            r = r.substring(0, r.length() - "</texto>".length());
        }
        return r.strip();
    }

    private static String base(Settings s) {
        return s.ollamaUrl.endsWith("/") ? s.ollamaUrl.substring(0, s.ollamaUrl.length() - 1) : s.ollamaUrl;
    }
}
