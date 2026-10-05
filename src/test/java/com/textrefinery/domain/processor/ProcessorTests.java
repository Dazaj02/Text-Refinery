package com.textrefinery.domain.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.ProcessingContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Una clase anidada por procesador. Los casos reales de PDF/web viven en los golden files. */
class ProcessorTests {

    private static final ProcessingContext CTX = ProcessingContext.of(CleaningProfile.WORD);

    @Nested
    class ControlChars {
        private final TextProcessor p = new ControlCharRemover();

        @Test
        void normalizaSaltosYQuitaInvisibles() {
            assertThat(p.process("a\r\nb\rc​d­e\u0007", CTX)).isEqualTo("a\nb\ncde");
        }
    }

    @Nested
    class Ligatures {
        @Test
        void reemplazaLigaduras() {
            assertThat(new LigatureFixer().process("ﬁnal ﬂujo eﬃcaz", CTX))
                    .isEqualTo("final flujo efficaz");
        }
    }

    @Nested
    class Hyphenation {
        private final TextProcessor p = new HyphenationFixer();

        @Test
        void unePalabraPartida() {
            assertThat(p.process("infor-\nmación útil", CTX)).isEqualTo("información útil");
        }

        @Test
        void respetaGuionSiSigueMayuscula() {
            assertThat(p.process("teórico-\nPráctico", CTX)).isEqualTo("teórico-\nPráctico");
        }
    }

    @Nested
    class LineBreaks {
        private final TextProcessor p = new LineBreakJoiner();

        @Test
        void unePorLineaPeroConservaParrafos() {
            assertThat(p.process("uno\ndos\n\ntres\ncuatro", CTX)).isEqualTo("uno dos\n\ntres cuatro");
        }

        @Test
        void conservaVinetasYTitulos() {
            assertThat(p.process("Lista:\n- a\n- b", CTX)).isEqualTo("Lista:\n- a\n- b");
            assertThat(p.process("# Título\ntexto\nmás", CTX)).isEqualTo("# Título\ntexto más");
        }
    }

    @Nested
    class Quotes {
        @Test
        void rectificaComillasTipograficas() {
            assertThat(new QuoteNormalizer().process("“hola” ‘x’ «ok»", CTX))
                    .isEqualTo("\"hola\" 'x' «ok»");
        }
    }

    @Nested
    class Whitespace {
        @Test
        void colapsaYRecorta() {
            assertThat(new WhitespaceNormalizer().process("  a  b \t c  \n \n\n\n d  ", CTX))
                    .isEqualTo("a b c\n\nd");
        }
    }

    @Nested
    class Html {
        @Test
        void quitaEtiquetasConservandoParrafos() {
            assertThat(new HtmlStripper().process("<p>Hola <b>mundo</b></p><p>Adiós</p>", CTX))
                    .contains("Hola mundo")
                    .contains("Adiós");
        }

        @Test
        void textoPlanoNoSeToca() {
            assertThat(new HtmlStripper().process("a < b y c > d", CTX)).isEqualTo("a < b y c > d");
        }

        @Test
        void convierteAMarkdown() {
            assertThat(new HtmlToMarkdownConverter().process("<h1>Título</h1><p>Texto <strong>fuerte</strong></p>", CTX))
                    .contains("# Título")
                    .contains("**fuerte**");
        }
    }

    @Nested
    class Latex {
        @Test
        void escapaCaracteresEspeciales() {
            assertThat(new LatexEscaper().process("50% & a_b #1 {x} $", CTX))
                    .isEqualTo("50\\% \\& a\\_b \\#1 \\{x\\} \\$");
        }
    }
}
