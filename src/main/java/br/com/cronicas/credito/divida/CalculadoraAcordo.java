package br.com.cronicas.credito.divida;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import br.com.cronicas.credito.politica.AlcadaOficial;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic arithmetic, and the place where policy is enforced rather than
 * merely consulted.
 *
 * <p>Two jobs, and both have to live together. The first is that no monetary
 * value in the system is ever produced by a language model. The second is less
 * obvious and easier to forget: a model that cannot invent a number can still
 * ask for one it has no business asking for. Nothing stops it proposing 90% off
 * a CONSIGNADO contract thirty days late, where the manual allows nothing, and a
 * simulation that returns that figure with no comment has laundered a bad
 * proposal into an authoritative-looking quote.
 *
 * <p>So the ceiling is applied here, on the way out, from the same
 * {@link AlcadaOficial} table that verifies the RAG answers in article two. The
 * simulation is still produced — an analyst needs to see what the customer asked
 * for — but it comes back labelled with the authority it requires. "O modelo
 * propoe, o codigo decide" is only true if the code actually looks.
 */
@Component
public class CalculadoraAcordo {

    /** Section 5: no instalment may be smaller than this, in any carteira. */
    private static final BigDecimal VALOR_MINIMO_PARCELA = new BigDecimal("80.00");

    /** Section 5: plans longer than this need the recovery manager. */
    private static final int MAXIMO_PARCELAS_SEM_GERENTE = 24;

    /** Section 5: plans longer than this require a down payment. */
    private static final int PARCELAS_COM_ENTRADA = 12;

    private static final BigDecimal PERCENTUAL_ENTRADA = new BigDecimal("0.10");

    private final AlcadaOficial alcadaOficial;

    public CalculadoraAcordo(AlcadaOficial alcadaOficial) {
        this.alcadaOficial = alcadaOficial;
    }

    public SimulacaoAcordo simular(PosicaoDivida posicao, BigDecimal percentualDesconto, int parcelas) {
        if (posicao == null) {
            throw new IllegalArgumentException("posicao is required");
        }
        if (percentualDesconto == null) {
            throw new IllegalArgumentException("percentualDesconto is required");
        }
        if (parcelas < 1) {
            throw new IllegalArgumentException("parcelas must be >= 1");
        }
        if (percentualDesconto.signum() < 0 || percentualDesconto.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("percentualDesconto must be between 0 and 100");
        }

        BigDecimal fator = BigDecimal.ONE.subtract(
                percentualDesconto.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));

        BigDecimal valorComDesconto = posicao.saldoAtualizado()
                .multiply(fator)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valorDaParcela = valorComDesconto
                .divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);

        BigDecimal entradaMinima = parcelas > PARCELAS_COM_ENTRADA
                ? valorComDesconto.multiply(PERCENTUAL_ENTRADA).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);

        Carteira carteira = posicao.carteira();
        int dias = posicao.diasEmAtraso();

        BigDecimal maximoPolitica = alcadaOficial.maximoParcelado(carteira, dias, parcelas);
        AprovacaoNecessaria aprovacao = aprovacaoPara(percentualDesconto, maximoPolitica, parcelas);

        return new SimulacaoAcordo(
                posicao.contrato(),
                carteira,
                dias,
                FaixaAtraso.de(dias),
                posicao.saldoAtualizado(),
                parcelas,
                percentualDesconto,
                maximoPolitica,
                aprovacao == AprovacaoNecessaria.NENHUMA,
                aprovacao,
                valorComDesconto,
                valorDaParcela,
                entradaMinima,
                restricoes(carteira, parcelas, valorComDesconto, valorDaParcela));
    }

    /**
     * Section 4. The 70% rule is checked first because it is absolute: it applies
     * in any carteira, regardless of how generous that carteira's band happens to
     * be. Checking the band first would quietly let an 85% CARTAO settlement
     * through on the operator's own authority.
     */
    private AprovacaoNecessaria aprovacaoPara(BigDecimal solicitado, BigDecimal maximoPolitica, int parcelas) {
        if (parcelas > MAXIMO_PARCELAS_SEM_GERENTE) {
            return AprovacaoNecessaria.GERENTE_RECUPERACAO;
        }
        if (solicitado.compareTo(AlcadaOficial.LIMITE_GERENTE) > 0) {
            return AprovacaoNecessaria.GERENTE_RECUPERACAO;
        }
        if (solicitado.compareTo(maximoPolitica) > 0) {
            return AprovacaoNecessaria.SUPERVISOR;
        }
        return AprovacaoNecessaria.NENHUMA;
    }

    private List<String> restricoes(Carteira carteira, int parcelas,
                                    BigDecimal valorComDesconto, BigDecimal valorDaParcela) {

        List<String> restricoes = new ArrayList<>();

        if (parcelas > MAXIMO_PARCELAS_SEM_GERENTE) {
            restricoes.add("Acima de %d parcelas, a politica exige aprovacao do gerente de recuperacao"
                    .formatted(MAXIMO_PARCELAS_SEM_GERENTE));
        }

        if (valorDaParcela.compareTo(VALOR_MINIMO_PARCELA) < 0) {
            int maximo = parcelasPeloValorMinimo(valorComDesconto);
            restricoes.add(("Parcela de R$ %s abaixo do minimo de R$ %s. "
                    + "Pela politica, reduza para no maximo %d parcela(s)")
                    .formatted(valorDaParcela.toPlainString(), VALOR_MINIMO_PARCELA.toPlainString(), maximo));
        }

        if (carteira == Carteira.CDC_VEICULO) {
            restricoes.add("CDC_VEICULO: se houver acao de busca e apreensao ajuizada, "
                    + "qualquer desconto depende de parecer da area de garantias");
        }

        return restricoes;
    }

    /**
     * How many instalments the discounted amount supports without breaching the
     * minimum. Floor, never round: one cent under the minimum is still under it.
     */
    private int parcelasPeloValorMinimo(BigDecimal valorComDesconto) {
        int maximo = valorComDesconto
                .divide(VALOR_MINIMO_PARCELA, 0, RoundingMode.DOWN)
                .intValue();
        return Math.max(maximo, 1);
    }
}
