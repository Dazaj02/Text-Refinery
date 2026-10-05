package com.textrefinery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.ProcessingContext;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Casos reales: golden/&lt;perfil&gt;/&lt;caso&gt;/entrada.txt y esperado.txt.
 * Cada caso comprueba el resultado y que limpiar dos veces no cambia nada (idempotencia),
 * salvo en LaTeX, donde escapar de nuevo es distinto por definición.
 */
class GoldenFileTest {

    private final PipelineFactory factory = new PipelineFactory();

    @TestFactory
    Stream<DynamicTest> casosGolden() throws IOException, URISyntaxException {
        Path root = Path.of(getClass().getResource("/golden").toURI());
        List<Path> cases;
        try (Stream<Path> walk = Files.walk(root, 2)) {
            cases = walk.filter(p -> Files.exists(p.resolve("entrada.txt"))).sorted(Comparator.naturalOrder()).toList();
        }
        return cases.stream().map(dir -> {
            CleaningProfile profile = CleaningProfile.valueOf(dir.getParent().getFileName().toString().toUpperCase(Locale.ROOT));
            return dynamicTest(profile + "/" + dir.getFileName(), () -> verify(dir, profile));
        });
    }

    private void verify(Path dir, CleaningProfile profile) throws IOException {
        String input = Files.readString(dir.resolve("entrada.txt"), StandardCharsets.UTF_8);
        String expected = Files.readString(dir.resolve("esperado.txt"), StandardCharsets.UTF_8).replace("\r\n", "\n").stripTrailing();

        var pipeline = factory.build(profile, Set.of());
        var ctx = ProcessingContext.of(profile);
        String result = pipeline.process(input, ctx);

        assertThat(result).isEqualTo(expected);
        if (profile != CleaningProfile.LATEX) {
            assertThat(pipeline.process(result, ctx)).as("idempotencia").isEqualTo(result);
        }
    }
}
