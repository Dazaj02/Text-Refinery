package com.textrefinery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.textrefinery.application.CleanClipboardUseCase;
import com.textrefinery.application.PipelineFactory;
import com.textrefinery.application.RefineWithAiUseCase;
import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.domain.port.AiProviderException;
import com.textrefinery.domain.port.ClipboardPort;
import com.textrefinery.domain.port.NotifierPort;
import com.textrefinery.infrastructure.ai.OllamaAiProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Prueba el adaptador contra un servidor HTTP real que imita la API de Ollama (sin necesitar Ollama instalado). */
class OllamaAiProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final Settings settings = new Settings();
    private final com.textrefinery.infrastructure.i18n.ResourceBundleMessages messages =
            new com.textrefinery.infrastructure.i18n.ResourceBundleMessages(com.textrefinery.domain.model.Language.ES);
    private HttpServer server;
    private volatile int status = 200;
    private volatile String reply = "{\"response\":\"  Texto refinado.  \"}";
    private volatile long delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", ex -> {
            requests.add(mapper.readTree(ex.getRequestBody()));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/api/tags", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        settings.ollamaUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        settings.aiModel = "modelo-de-prueba";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void enviaLaPeticionYDevuelveLaRespuesta() {
        String result = new OllamaAiProvider(() -> settings).refine("hola mundo", AiMode.GRAMMAR);

        assertThat(result.strip()).isEqualTo("Texto refinado.");
        JsonNode sent = requests.get(0);
        assertThat(sent.get("model").asText()).isEqualTo("modelo-de-prueba");
        assertThat(sent.get("stream").asBoolean()).isFalse();
        assertThat(sent.get("options").get("temperature").asDouble()).isLessThanOrEqualTo(0.3);
        assertThat(sent.get("options").get("num_ctx").asInt()).isGreaterThanOrEqualTo(8192);
        assertThat(sent.get("prompt").asText()).contains("hola mundo");
    }

    @Test
    void quitaLasEtiquetasDelPromptSiElModeloLasDevuelve() {
        reply = "{\"response\":\"<texto>\\nHola, ¿cómo estás?\\n</texto>\"}";
        assertThat(new OllamaAiProvider(() -> settings).refine("hola", AiMode.GRAMMAR)).isEqualTo("Hola, ¿cómo estás?");
    }

    @Test
    void elIdiomaElegidoLlegaAlPromptQueSeEnvia() {
        settings.textLanguage = com.textrefinery.domain.model.Language.EN;
        new OllamaAiProvider(() -> settings).refine("hello there", AiMode.GRAMMAR);
        assertThat(requests.get(0).get("prompt").asText()).contains("English copy editor").doesNotContain("corrector");

        settings.textLanguage = com.textrefinery.domain.model.Language.ES;
        new OllamaAiProvider(() -> settings).refine("hola", AiMode.GRAMMAR);
        assertThat(requests.get(1).get("prompt").asText()).contains("corrector de estilo");
    }

    @Test
    void detectaQueElServidorEstaDisponible() {
        assertThat(new OllamaAiProvider(() -> settings).isAvailable()).isTrue();
    }

    @Test
    void siElServidorCaeLoDetectaYLanzaUnErrorControlado() {
        server.stop(0);
        OllamaAiProvider provider = new OllamaAiProvider(() -> settings);
        assertThat(provider.isAvailable()).isFalse();
        assertThatThrownBy(() -> provider.refine("hola", AiMode.GRAMMAR)).isInstanceOf(AiProviderException.class);
    }

    @Test
    void unaRespuestaHttpDeErrorSeConvierteEnExcepcion() {
        status = 500;
        reply = "{\"error\":\"model not found\"}";
        assertThatThrownBy(() -> new OllamaAiProvider(() -> settings).refine("hola", AiMode.CITE))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("500");
    }

    @Test
    void conLaIaCaidaElCasoDeUsoDejaSoloLaLimpieza() {
        server.stop(0);
        String[] clipboard = {"infor-\nmacion   sucia"};
        ClipboardPort fake = new ClipboardPort() {
            public Optional<String> readText() { return Optional.of(clipboard[0]); }
            public Optional<String> readHtml() { return Optional.empty(); }
            public boolean writeText(String t) { clipboard[0] = t; return true; }
        };
        List<String> errors = new ArrayList<>();
        NotifierPort notifier = new NotifierPort() {
            public void info(String m) { }
            public void error(String m) { errors.add(m); }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CleanClipboardUseCase clean = new CleanClipboardUseCase(fake, new PipelineFactory(), notifier, () -> settings, messages);
            new RefineWithAiUseCase(clean, new OllamaAiProvider(() -> settings), fake, notifier, () -> settings, pool, messages)
                    .execute(AiMode.GRAMMAR).join();
        } finally {
            pool.shutdownNow();
        }
        assertThat(clipboard[0]).isEqualTo("informacion sucia");
        assertThat(errors).hasSize(1);
    }

    @Test
    void siLaIaTardaMasDelLimiteSeAplicaSoloLaLimpieza() {
        delayMs = 3_000;
        settings.aiTimeoutSeconds = 1;
        String[] clipboard = {"hola   mundo"};
        ClipboardPort fake = new ClipboardPort() {
            public Optional<String> readText() { return Optional.of(clipboard[0]); }
            public Optional<String> readHtml() { return Optional.empty(); }
            public boolean writeText(String t) { clipboard[0] = t; return true; }
        };
        List<String> errors = new ArrayList<>();
        NotifierPort notifier = new NotifierPort() {
            public void info(String m) { }
            public void error(String m) { errors.add(m); }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CleanClipboardUseCase clean = new CleanClipboardUseCase(fake, new PipelineFactory(), notifier, () -> settings, messages);
            new RefineWithAiUseCase(clean, new OllamaAiProvider(() -> settings), fake, notifier, () -> settings, pool, messages)
                    .execute(AiMode.SUMMARIZE).join();
        } finally {
            pool.shutdownNow();
        }
        assertThat(clipboard[0]).isEqualTo("hola mundo");
        assertThat(errors).hasSize(1);
    }

    // ------------------------------------------------ arranque automático de Ollama

    /** Lanzador falso: cuenta las llamadas y, si se le pide, "arranca" el servidor de prueba en el mismo puerto. */
    private final class FakeLauncher extends com.textrefinery.infrastructure.ai.OllamaLauncher {
        int starts;
        boolean succeeds = true;
        int port;

        @Override
        public synchronized boolean start() {
            starts++;
            if (succeeds) {
                try {
                    server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                    server.createContext("/api/tags", ex -> {
                        ex.sendResponseHeaders(200, -1);
                        ex.close();
                    });
                    server.createContext("/api/generate", ex -> {
                        ex.getRequestBody().readAllBytes();
                        byte[] body = reply.getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, body.length);
                        ex.getResponseBody().write(body);
                        ex.close();
                    });
                    server.start();
                } catch (IOException e) {
                    return false;
                }
            }
            return succeeds;
        }
    }

    @Test
    void siOllamaNoRespondeYElAjusteEstaActivoLoArrancaYEspera() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.port = server.getAddress().getPort();
        server.stop(0);
        settings.autoStartOllama = true;

        assertThat(new OllamaAiProvider(() -> settings, launcher).ensureAvailable(settings)).isTrue();
        assertThat(launcher.starts).isEqualTo(1);
    }

    @Test
    void conElAjusteApagadoNuncaArrancaOllama() {
        FakeLauncher launcher = new FakeLauncher();
        server.stop(0);
        settings.autoStartOllama = false;

        assertThat(new OllamaAiProvider(() -> settings, launcher).ensureAvailable(settings)).isFalse();
        assertThat(launcher.starts).isZero();
    }

    @Test
    void siYaEstabaEnMarchaNoLoVuelveAArrancar() {
        FakeLauncher launcher = new FakeLauncher();
        settings.autoStartOllama = true;

        assertThat(new OllamaAiProvider(() -> settings, launcher).ensureAvailable(settings)).isTrue();
        assertThat(launcher.starts).isZero();
    }

    @Test
    void conUnaDireccionRemotaNoArrancaNada() {
        FakeLauncher launcher = new FakeLauncher();
        settings.autoStartOllama = true;
        settings.ollamaUrl = "http://192.0.2.1:11434";

        assertThat(new OllamaAiProvider(() -> settings, launcher).ensureAvailable(settings)).isFalse();
        assertThat(launcher.starts).isZero();
    }

    @Test
    void siOllamaNoSePuedeArrancarSeDevuelveFalseSinEsperar() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.succeeds = false;
        server.stop(0);
        settings.autoStartOllama = true;

        long t0 = System.nanoTime();
        assertThat(new OllamaAiProvider(() -> settings, launcher).ensureAvailable(settings)).isFalse();
        assertThat(launcher.starts).isEqualTo(1);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(5_000);
    }

    @Test
    void refinarArrancaOllamaSiHaceFalta() {
        FakeLauncher launcher = new FakeLauncher();
        launcher.port = server.getAddress().getPort();
        server.stop(0);
        settings.autoStartOllama = true;

        assertThat(new OllamaAiProvider(() -> settings, launcher).refine("hola", AiMode.GRAMMAR).strip())
                .isEqualTo("Texto refinado.");
        assertThat(launcher.starts).isEqualTo(1);
    }

    @Test
    void elLanzadorSoloArrancaServidoresLocales() {
        assertThat(com.textrefinery.infrastructure.ai.OllamaLauncher.isLocal("http://localhost:11434")).isTrue();
        assertThat(com.textrefinery.infrastructure.ai.OllamaLauncher.isLocal("http://127.0.0.1:11434/")).isTrue();
        assertThat(com.textrefinery.infrastructure.ai.OllamaLauncher.isLocal("http://[::1]:11434")).isTrue();
        assertThat(com.textrefinery.infrastructure.ai.OllamaLauncher.isLocal("http://otro-equipo:11434")).isFalse();
        assertThat(com.textrefinery.infrastructure.ai.OllamaLauncher.isLocal("no es una url")).isFalse();
    }
}
