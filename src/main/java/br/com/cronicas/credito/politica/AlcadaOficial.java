package br.com.cronicas.credito.politica;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The discount authority table, as code.
 *
 * <p>"If the table is already in code, what is RAG for?" is the right question,
 * and it has an answer. The manual has ten sections; this is one of them. Most
 * questions an analyst asks are about conduct, deadlines, vulnerability handling
 * or agreement breakage, and none of those reduce to a lookup. But the questions
 * that produce a number are exactly the ones that cost money when wrong, and
 * those are the ones worth checking against something deterministic.
 *
 * <p>Retrieval answers the question. This verifies the answer — and, through
 * {@link #maximoParcelado}, is also what bounds the simulations the model is
 * allowed to propose.
 *
 * <p>The table is keyed by two enums and verified complete at startup, so
 * {@link #maximo} is total: there is no "authority unknown" branch anywhere
 * downstream, and a hole in the table fails the context refresh instead of
 * surfacing as a null three layers away.
 */
@Component
public class AlcadaOficial {

    /** Section 4: above this, in any carteira, the recovery manager has to sign off. */
    public static final BigDecimal LIMITE_GERENTE = new BigDecimal("70");

    private static final Map<Carteira, Map<FaixaAtraso, BigDecimal>> TABELA = tabela();

    public AlcadaOficial() {
        for (Carteira carteira : Carteira.values()) {
            for (FaixaAtraso faixa : FaixaAtraso.values()) {
                if (TABELA.get(carteira) == null || TABELA.get(carteira).get(faixa) == null) {
                    throw new IllegalStateException(
                            "Alcada ausente na tabela: %s / %s".formatted(carteira, faixa));
                }
            }
        }
    }

    /**
     * Ceiling for a lump-sum settlement, per section 4 of the manual.
     */
    public BigDecimal maximo(Carteira carteira, int diasEmAtraso) {
        return TABELA.get(carteira).get(FaixaAtraso.de(diasEmAtraso));
    }

    /**
     * Ceiling for an instalment plan, per section 5: paying over time stretches
     * the exposure, so the lump-sum ceiling is cut by a fixed number of
     * percentage points as the plan gets longer.
     *
     * <p>Above 24 instalments the manual does not set a ceiling at all — it
     * requires the recovery manager. The deepest published reduction is applied
     * here so the number is still bounded, and
     * {@link br.com.cronicas.credito.divida.CalculadoraAcordo} raises the
     * approval level separately.
     */
    public BigDecimal maximoParcelado(Carteira carteira, int diasEmAtraso, int parcelas) {
        if (parcelas < 1) {
            throw new IllegalArgumentException("parcelas deve ser >= 1: " + parcelas);
        }

        BigDecimal aVista = maximo(carteira, diasEmAtraso);
        BigDecimal reduzido = aVista.subtract(reducaoPorParcelamento(parcelas));

        return reduzido.signum() < 0 ? BigDecimal.ZERO : reduzido;
    }

    /** Percentage points subtracted from the lump-sum ceiling. Section 5. */
    private BigDecimal reducaoPorParcelamento(int parcelas) {
        if (parcelas == 1) {
            return BigDecimal.ZERO;
        }
        if (parcelas <= 6) {
            return BigDecimal.TEN;
        }
        if (parcelas <= 12) {
            return new BigDecimal("20");
        }
        return new BigDecimal("30");
    }

    private static Map<Carteira, Map<FaixaAtraso, BigDecimal>> tabela() {
        Map<Carteira, Map<FaixaAtraso, BigDecimal>> tabela = new EnumMap<>(Carteira.class);

        tabela.put(Carteira.CONSIGNADO, linha("0", "10", "20", "30", "45"));
        tabela.put(Carteira.CARTAO, linha("0", "25", "45", "65", "85"));
        tabela.put(Carteira.CDC_VEICULO, linha("0", "12", "22", "35", "50"));
        tabela.put(Carteira.PESSOAL, linha("0", "20", "35", "55", "75"));

        return Map.copyOf(tabela);
    }

    private static Map<FaixaAtraso, BigDecimal> linha(String... percentuais) {
        FaixaAtraso[] faixas = FaixaAtraso.values();
        if (percentuais.length != faixas.length) {
            throw new IllegalStateException("A linha da tabela deve ter um percentual por faixa");
        }

        Map<FaixaAtraso, BigDecimal> linha = new EnumMap<>(FaixaAtraso.class);
        for (int i = 0; i < faixas.length; i++) {
            linha.put(faixas[i], new BigDecimal(percentuais[i]));
        }
        return linha;
    }
}
