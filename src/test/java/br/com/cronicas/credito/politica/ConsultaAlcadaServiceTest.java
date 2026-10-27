package br.com.cronicas.credito.politica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * The verification logic, tested without a model.
 *
 * <p>This is the class article two is really about, and until now the only thing
 * that exercised it was an integration test that needed Docker and Ollama and
 * could only produce the answers the model happened to give that day. A
 * divergence between the manual and the table is precisely the case you cannot
 * ask a live model to reproduce on demand — so the retrieval is stubbed and the
 * interesting inputs are handed over directly.
 *
 * <p>Stubbing the model is not cheating. The question here is not "does the model
 * answer well", it is "when the model answers badly, does this code notice",
 * and that question needs a bad answer on purpose.
 */
class ConsultaAlcadaServiceTest {

    private final PoliticaQueryService politica = mock(PoliticaQueryService.class);
    private final ConsultaAlcadaService consulta = new ConsultaAlcadaService(politica, new AlcadaOficial());

    @Test
    void agreesWithTheTableWhenTheModelReadsTheRightRow() {
        responde(new RespostaPolitica("4.2 Alcada da carteira CARTAO",
                "Ate 45% do saldo atualizado.", new BigDecimal("45"), true));

        var resultado = consulta.consultar("Qual o desconto maximo?", Carteira.CARTAO, 180);

        assertThat(resultado.percentualOficial()).isEqualByComparingTo("45");
        assertThat(resultado.faixa()).isEqualTo(FaixaAtraso.F3_91_A_180);
        assertThat(resultado.divergente()).isFalse();
        assertThat(resultado.semCitacao()).isFalse();
        assertThat(resultado.confiavel()).isTrue();
    }

    @Test
    void flagsTheAnswerThatReadTheNextRowDown() {
        // 65% is the 181-to-360 figure for CARTAO. A contract at 180 days is in
        // the band below it. Fluent, specific, cites a real section, and twenty
        // points wrong -- the failure that retrieval alone does not catch.
        responde(new RespostaPolitica("4.2 Alcada da carteira CARTAO",
                "Ate 65% do saldo atualizado.", new BigDecimal("65"), true));

        var resultado = consulta.consultar("Qual o desconto maximo?", Carteira.CARTAO, 180);

        assertThat(resultado.percentualOficial()).isEqualByComparingTo("45");
        assertThat(resultado.divergente()).isTrue();
        assertThat(resultado.confiavel()).isFalse();
    }

    @Test
    void flagsAnAnswerThatCitesNothing() {
        responde(new RespostaPolitica("  ", "O desconto depende da negociacao.", null, true));

        var resultado = consulta.consultar("Como funciona o desconto?", Carteira.PESSOAL, 100);

        assertThat(resultado.semCitacao()).isTrue();
        assertThat(resultado.confiavel()).isFalse();
    }

    @Test
    void doesNotFlagDivergenceWhenTheQuestionWasNotAboutAPercentage() {
        responde(new RespostaPolitica("7. Conduta de cobranca",
                "Em dias uteis, das 8h as 20h.", null, true));

        var resultado = consulta.consultar("Em que horario posso ligar?", Carteira.CARTAO, 180);

        assertThat(resultado.divergente()).isFalse();
        assertThat(resultado.confiavel()).isTrue();
        // The table is still reported, so a caller can show the authority next to
        // an answer that did not mention it.
        assertThat(resultado.percentualOficial()).isEqualByComparingTo("45");
    }

    @Test
    void doesNotFlagAMissingCitationWhenTheModelDeclinedToAnswer() {
        responde(new RespostaPolitica("", "Nao encontrei isso no manual.", null, false));

        var resultado = consulta.consultar("Qual a taxa de cambio?", Carteira.CARTAO, 90);

        assertThat(resultado.semCitacao()).isFalse();
        assertThat(resultado.confiavel()).isTrue();
    }

    private void responde(RespostaPolitica resposta) {
        when(politica.perguntar(anyString(), any(Carteira.class))).thenReturn(resposta);
    }
}
