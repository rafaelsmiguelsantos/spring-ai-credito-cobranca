package br.com.cronicas.credito.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every boundary, pinned.
 *
 * <p>This is four lines of production code and it gets a parameterised test over
 * both sides of all four edges, because the bug this prevents is not a crash. It
 * is a contract at 181 days quoted the 91-to-180 discount, which looks like a
 * correct answer, passes review, and is only noticed in an audit sample months
 * later. Cheap code with expensive failure modes is exactly what deserves
 * exhaustive tests.
 */
class FaixaAtrasoTest {

    @ParameterizedTest
    @CsvSource({
            "0,   F1_ATE_30",
            "1,   F1_ATE_30",
            "30,  F1_ATE_30",
            "31,  F2_31_A_90",
            "90,  F2_31_A_90",
            "91,  F3_91_A_180",
            "180, F3_91_A_180",
            "181, F4_181_A_360",
            "360, F4_181_A_360",
            "361, F5_ACIMA_360",
            "5000, F5_ACIMA_360"
    })
    void mapsDaysToBand(int dias, FaixaAtraso esperada) {
        assertThat(FaixaAtraso.de(dias)).isEqualTo(esperada);
    }

    @Test
    void rejectsNegativeDays() {
        assertThatThrownBy(() -> FaixaAtraso.de(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void everyBandDescribesItself() {
        for (FaixaAtraso faixa : FaixaAtraso.values()) {
            assertThat(faixa.descricao()).as("%s", faixa).isNotBlank();
        }
    }
}
