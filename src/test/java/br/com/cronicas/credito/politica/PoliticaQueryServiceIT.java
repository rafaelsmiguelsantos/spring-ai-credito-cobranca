package br.com.cronicas.credito.politica;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.cronicas.credito.TestcontainersConfig;
import br.com.cronicas.credito.dominio.Carteira;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The test that matters for a RAG feature.
 *
 * <p>The naive version of this test asks a question and eyeballs the answer. The
 * useful version asserts on the two things that actually break in production:
 * the pipeline cites the section it used, and the number it reports matches the
 * authority table.
 *
 * <p>{@link #refusesWhenTheAnswerIsNotInTheManual()} is the test nobody writes
 * and everybody needs. A retrieval system that answers every question is not
 * working well — it is failing silently.
 *
 * <p>What is deliberately no longer here: the cell-by-cell check of the authority
 * table against the manual. It asserted on pure code and lived in a test that
 * needs Docker, a model server and a few minutes, which meant the fastest check
 * in the suite ran at the speed of the slowest. It is now
 * {@link AlcadaOficialTest}.
 *
 * <p>Ingestion runs once for the class, not once per test. It is idempotent now,
 * so repeating it would be correct — just four embedding round-trips slower each
 * time, for nothing.
 */
@SpringBootTest
@ActiveProfiles("ollama")
@Import(TestcontainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PoliticaQueryServiceIT {

    @Autowired
    PoliticaIngestionService ingestion;

    @Autowired
    ConsultaAlcadaService consulta;

    @Autowired
    PoliticaProperties properties;

    @BeforeAll
    void ingest() {
        var resultado = ingestion.ingest(properties.manual());
        assertThat(resultado.secoesIngeridas()).isGreaterThan(10);
    }

    @Test
    void answersTheDiscountAuthorityAndAgreesWithTheTable() {
        var resultado = consulta.consultar(
                "Qual o desconto maximo a vista para um contrato com 180 dias de atraso?",
                Carteira.CARTAO, 180);

        assertThat(resultado.resposta().respondido()).isTrue();
        assertThat(resultado.resposta().secaoCitada()).containsIgnoringCase("CARTAO");
        assertThat(resultado.resposta().percentualMaximo()).isEqualByComparingTo("45");
        assertThat(resultado.divergente()).isFalse();
        assertThat(resultado.confiavel()).isTrue();
    }

    @Test
    void doesNotLeakTheAuthorityOfAnotherCarteira() {
        var resultado = consulta.consultar(
                "Qual o desconto maximo a vista para um contrato com 180 dias de atraso?",
                Carteira.CONSIGNADO, 180);

        // 45 is the CARTAO answer to the same question. Four near-identical
        // sections differing by one noun and five numbers is precisely the case
        // where similarity search alone is not enough, and the metadata filter is.
        assertThat(resultado.resposta().percentualMaximo()).isEqualByComparingTo("20");
        assertThat(resultado.divergente()).isFalse();
    }

    @Test
    void respectsTheBoundaryBetweenRanges() {
        // 181 crosses into the next band. Off-by-one at a range boundary is the
        // most common wrong answer this pipeline can give.
        var resultado = consulta.consultar(
                "Qual o desconto maximo a vista para um contrato com 181 dias de atraso?",
                Carteira.CARTAO, 181);

        assertThat(resultado.resposta().percentualMaximo()).isEqualByComparingTo("65");
    }

    @Test
    void stillReachesTheSectionsThatApplyToEveryCarteira() {
        // The carteira filter has to narrow the carteira-specific sections without
        // hiding the rest of the manual. Conduct, vulnerability and bureau rules
        // are most of the document and belong to no carteira, and a filter that
        // drops them fails in the one direction nothing else here would catch:
        // the authority questions keep working.
        var resultado = consulta.consultar(
                "Em que horarios a cobranca pode entrar em contato com o cliente?",
                Carteira.CARTAO, 47);

        assertThat(resultado.resposta().respondido()).isTrue();
        assertThat(resultado.resposta().secaoCitada()).isNotBlank();
        assertThat(resultado.resposta().resposta()).containsIgnoringCase("8");
    }

    @Test
    void refusesWhenTheAnswerIsNotInTheManual() {
        var resultado = consulta.consultar(
                "Qual a taxa de cambio do dolar para acordos em moeda estrangeira?",
                Carteira.CARTAO, 90);

        assertThat(resultado.resposta().respondido()).isFalse();
        assertThat(resultado.resposta().percentualMaximo()).isNull();
    }
}
