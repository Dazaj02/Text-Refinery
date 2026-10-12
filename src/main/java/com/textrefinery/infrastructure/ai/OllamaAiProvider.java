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

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Supplier<Settings> settings;

    public OllamaAiProvider(Supplier<Settings> settings) {
        this.settings = settings;
    }

    @Override
    public String refine(String text, AiMode mode) {
        Settings s = settings.get();
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
            return json.path("response").asText("");
        } catch (IOException e) {
            throw new AiProviderException("No se pudo contactar con Ollama", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Llamada a Ollama interrumpida", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base(settings.get()) + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String base(Settings s) {
        return s.ollamaUrl.endsWith("/") ? s.ollamaUrl.substring(0, s.ollamaUrl.length() - 1) : s.ollamaUrl;
    }
}
