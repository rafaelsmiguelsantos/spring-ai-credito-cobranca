package br.com.cronicas.credito.politica;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.cronicas.credito.dominio.Carteira;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * The chunker, tested without a database or a model.
 *
 * <p>Chunking is where most of the quality of a RAG pipeline is decided, and it
 * is also the one stage that is pure, fast and completely deterministic. Testing
 * it through a live retrieval — the usual approach, because the retrieval is the
 * interesting part — means a chunking bug shows up as a vaguely worse answer,
 * two services away from its cause.
 *
 * <p>So the metadata contract gets asserted directly: every chunk carries a
 * carteira, the heading travels with the text, and a subsection inherits the
 * carteira of the section above it.
 */
class ManualCobrancaReaderTest {

    private static final String SINTETICO = """
            ---
            documento: Manual de Teste
            versao: "9.9"
            ---

            # Titulo do documento

            Texto solto, fora de qualquer secao.

            ## 1. Geral

            Regra que vale para todas as carteiras.

            ## 2. Alcadas

            Introducao das alcadas.

            ### 2.1 Alcada da carteira CARTAO

            Faixa 2, de 31 a 90 dias: ate 25% do saldo atualizado.

            ## 3. Alcada da carteira PESSOAL

            Introducao da carteira pessoal.

            ### 3.1 Detalhes operacionais

            Um detalhe que nao repete o nome da carteira.
            """;

    @Test
    void emitsOneDocumentPerSection() {
        List<Document> documentos = ler(SINTETICO);

        assertThat(documentos).extracting(d -> d.getMetadata().get(MetadadosPolitica.SECAO))
                .containsExactly(
                        "1. Geral",
                        "2. Alcadas",
                        "2.1 Alcada da carteira CARTAO",
                        "3. Alcada da carteira PESSOAL",
                        "3.1 Detalhes operacionais");
    }

    @Test
    void tagsEverySectionWithACarteiraOrTheGeneralSentinel() {
        // The regression this pins: leaving carteira absent on general sections
        // makes them unreachable through the retrieval filter, silently. Every
        // chunk carries the key, always.
        List<Document> documentos = ler(SINTETICO);

        assertThat(documentos)
                .allSatisfy(d -> assertThat(d.getMetadata()).containsKey(MetadadosPolitica.CARTEIRA))
                .extracting(d -> d.getMetadata().get(MetadadosPolitica.CARTEIRA))
                .containsExactly(
                        MetadadosPolitica.CARTEIRA_GERAL,
                        MetadadosPolitica.CARTEIRA_GERAL,
                        Carteira.CARTAO.name(),
                        Carteira.PESSOAL.name(),
                        Carteira.PESSOAL.name());
    }

    @Test
    void makesASubsectionInheritTheCarteiraOfItsParent() {
        Document detalhes = secao(ler(SINTETICO), "3.1 Detalhes operacionais");

        assertThat(detalhes.getMetadata().get(MetadadosPolitica.CARTEIRA))
                .isEqualTo(Carteira.PESSOAL.name());
        assertThat(detalhes.getMetadata().get(MetadadosPolitica.SECAO_PAI))
                .isEqualTo("3. Alcada da carteira PESSOAL");
    }

    @Test
    void prependsTheHeadingToTheTextSoTheEmbeddingCarriesIt() {
        Document alcada = secao(ler(SINTETICO), "2.1 Alcada da carteira CARTAO");

        assertThat(alcada.getText())
                .startsWith("2.1 Alcada da carteira CARTAO")
                .contains("ate 25% do saldo atualizado");
    }

    @Test
    void carriesTheFrontmatterAndTheSourceOnEveryChunk() {
        List<Document> documentos = ler(SINTETICO);

        assertThat(documentos).allSatisfy(d -> {
            assertThat(d.getMetadata()).containsEntry("documento", "Manual de Teste");
            assertThat(d.getMetadata()).containsEntry("versao", "9.9");
            assertThat(d.getMetadata()).containsEntry(MetadadosPolitica.FONTE, "manual-de-teste.md");
        });
    }

    @Test
    void readsTheRealManualWithTheFourAuthoritySectionsTaggedCorrectly() {
        Resource manual = new ClassPathResource("politica/manual-cobranca-v3.md");
        List<Document> documentos = new ManualCobrancaReader(manual).read();

        assertThat(documentos).hasSizeGreaterThan(10);
        assertThat(documentos).allSatisfy(d ->
                assertThat(d.getMetadata()).containsEntry(MetadadosPolitica.FONTE, "manual-cobranca-v3.md"));

        for (Carteira carteira : Carteira.values()) {
            assertThat(documentos)
                    .as("secao de alcada da carteira %s", carteira)
                    .anySatisfy(d -> {
                        assertThat(d.getMetadata().get(MetadadosPolitica.CARTEIRA)).isEqualTo(carteira.name());
                        assertThat(d.getText()).contains("do saldo atualizado");
                    });
        }

        // Conduct, vulnerability and bureau rules apply to everyone, and are the
        // chunks a carteira filter must not exclude.
        assertThat(documentos)
                .filteredOn(d -> MetadadosPolitica.CARTEIRA_GERAL
                        .equals(d.getMetadata().get(MetadadosPolitica.CARTEIRA)))
                .hasSizeGreaterThan(4);
    }

    private List<Document> ler(String markdown) {
        Resource resource = new ByteArrayResource(markdown.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "manual-de-teste.md";
            }
        };
        return new ManualCobrancaReader(resource).read();
    }

    private Document secao(List<Document> documentos, String titulo) {
        return documentos.stream()
                .filter(d -> titulo.equals(d.getMetadata().get(MetadadosPolitica.SECAO)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("secao nao encontrada: " + titulo));
    }
}
