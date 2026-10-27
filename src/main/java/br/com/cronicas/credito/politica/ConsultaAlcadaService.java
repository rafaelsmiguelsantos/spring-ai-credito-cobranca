package br.com.cronicas.credito.politica;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Asks the manual, then checks the answer.
 *
 * <p>This is the part most RAG write-ups leave out. Retrieval reduces
 * hallucination; it does not remove it. The model can pull the right section and
 * still read the wrong row of it, and "ate 45%" versus "ate 65%" on a
 * five-thousand-real balance is a real loss that no amount of fluency will flag.
 *
 * <p>So the number is verified. When the manual's answer and the table disagree,
 * the discrepancy is surfaced rather than silently resolved: a mismatch usually
 * means the manual changed and the code did not, and that is worth a human
 * looking at it.
 *
 * <p>Two checks, not one. The second is cheaper and catches a different failure:
 * an answer that claims to be grounded but cites nothing. A model that has been
 * told to always fill {@code secaoCitada} and returns it empty while still
 * answering has not retrieved its answer from anywhere, whatever the prose says.
 */
@Service
public class ConsultaAlcadaService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaAlcadaService.class);

    /**
     * @param percentualOficial the authority from the table, always present
     * @param divergente        the model's percentage and the table disagree
     * @param semCitacao        the model answered without naming a section
     */
    public record ConsultaResultado(
            RespostaPolitica resposta,
            Carteira carteira,
            int diasEmAtraso,
            FaixaAtraso faixa,
            BigDecimal percentualOficial,
            boolean divergente,
            boolean semCitacao) {

        /** True when nothing about this answer needs a second pair of eyes. */
        public boolean confiavel() {
            return !divergente && !semCitacao;
        }
    }

    private final PoliticaQueryService politica;
    private final AlcadaOficial alcadaOficial;

    public ConsultaAlcadaService(PoliticaQueryService politica, AlcadaOficial alcadaOficial) {
        this.politica = politica;
        this.alcadaOficial = alcadaOficial;
    }

    public ConsultaResultado consultar(String pergunta, Carteira carteira, int diasEmAtraso) {
        RespostaPolitica resposta = politica.perguntar(pergunta, carteira);

        FaixaAtraso faixa = FaixaAtraso.de(diasEmAtraso);
        BigDecimal oficial = alcadaOficial.maximo(carteira, diasEmAtraso);

        boolean divergente = resposta.percentualMaximo() != null
                && oficial.compareTo(resposta.percentualMaximo()) != 0;

        boolean semCitacao = resposta.respondido() && !StringUtils.hasText(resposta.secaoCitada());

        if (divergente) {
            log.warn("ai.alcada.divergencia carteira={} dias={} faixa={} modelo={} tabela={} secao='{}'",
                    carteira, diasEmAtraso, faixa, resposta.percentualMaximo(), oficial, resposta.secaoCitada());
        }
        if (semCitacao) {
            log.warn("ai.alcada.sem_citacao carteira={} dias={} pergunta='{}'",
                    carteira, diasEmAtraso, pergunta);
        }

        return new ConsultaResultado(resposta, carteira, diasEmAtraso, faixa, oficial, divergente, semCitacao);
    }
}
