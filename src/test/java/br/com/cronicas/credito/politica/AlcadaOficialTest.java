package br.com.cronicas.credito.politica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The authority table against the manual, cell by cell.
 *
 * <p>This test used to live inside {@code PoliticaQueryServiceIT}, which meant
 * that checking a hard-coded table against a hard-coded document required Docker
 * and a running model server. It asserts on pure arithmetic and now runs in
 * milliseconds with {@code mvn test}, which is the difference between a check
 * that runs on every save and one that runs when someone remembers.
 *
 * <p>What it guards is drift. The table in {@link AlcadaOficial} and section 4 of
 * {@code manual-cobranca-v3.md} say the same thing twice, in two languages, and
 * the whole verification story in article two depends on them agreeing. When this
 * test fails, one of the two was updated and the other was not.
 */
class AlcadaOficialTest {

    private final AlcadaOficial alcada = new AlcadaOficial();

    @ParameterizedTest
    @CsvSource({
            // section 4.1
            "CONSIGNADO,  15, 0", "CONSIGNADO,  60, 10", "CONSIGNADO, 120, 20",
            "CONSIGNADO, 200, 30", "CONSIGNADO, 400, 45",
            // section 4.2
            "CARTAO,      15, 0", "CARTAO,      60, 25", "CARTAO,     120, 45",
            "CARTAO,     200, 65", "CARTAO,     400, 85",
            // section 4.3
            "CDC_VEICULO, 15, 0", "CDC_VEICULO, 60, 12", "CDC_VEICULO, 120, 22",
            "CDC_VEICULO, 200, 35", "CDC_VEICULO, 400, 50",
            // section 4.4
            "PESSOAL,     15, 0", "PESSOAL,     60, 20", "PESSOAL,    120, 35",
            "PESSOAL,    200, 55", "PESSOAL,    400, 75"
    })
    void matchesTheManualForEveryCarteiraAndBand(Carteira carteira, int dias, String esperado) {
        assertThat(alcada.maximo(carteira, dias))
                .as("%s / %d dias", carteira, dias)
                .isEqualByComparingTo(esperado);
    }

    @Test
    void coversEveryCombinationOfCarteiraAndBand() {
        for (Carteira carteira : Carteira.values()) {
            for (FaixaAtraso faixa : FaixaAtraso.values()) {
                int diasNaFaixa = diasRepresentativos(faixa);
                assertThat(alcada.maximo(carteira, diasNaFaixa))
                        .as("%s / %s", carteira, faixa)
                        .isNotNull();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            // section 5: the lump-sum ceiling drops as the plan gets longer
            "1,  45", "2,  35", "6,  35", "7,  25", "12, 25", "13, 15", "24, 15", "36, 15"
    })
    void reducesTheCeilingByPlanLength(int parcelas, String esperado) {
        // CARTAO at 120 days is band 3: 45% in a lump sum
        assertThat(alcada.maximoParcelado(Carteira.CARTAO, 120, parcelas))
                .as("%d parcelas", parcelas)
                .isEqualByComparingTo(esperado);
    }

    @Test
    void neverReturnsANegativeCeiling() {
        // CONSIGNADO at 60 days allows 10% in a lump sum. Twenty points off that
        // is not minus ten percent of the balance; it is no discount at all.
        assertThat(alcada.maximoParcelado(Carteira.CONSIGNADO, 60, 8))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsAPlanWithNoInstalments() {
        assertThatThrownBy(() -> alcada.maximoParcelado(Carteira.CARTAO, 120, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private int diasRepresentativos(FaixaAtraso faixa) {
        return switch (faixa) {
            case F1_ATE_30 -> 10;
            case F2_31_A_90 -> 60;
            case F3_91_A_180 -> 120;
            case F4_181_A_360 -> 200;
            case F5_ACIMA_360 -> 400;
        };
    }
}
