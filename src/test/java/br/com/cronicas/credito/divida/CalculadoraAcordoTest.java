package br.com.cronicas.credito.divida;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import br.com.cronicas.credito.politica.AlcadaOficial;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * No AI here, on purpose. The money math is an ordinary unit test with exact
 * expected values, because it is ordinary code. Every number the system shows a
 * customer comes through this class, and that is what makes the rest of the
 * application safe to put a language model in front of.
 *
 * <p>Half of these tests are not about arithmetic at all. They are about what the
 * calculator refuses to wave through: a discount above the operator's authority,
 * a plan longer than the manual allows, an instalment below the floor. A model
 * that cannot invent a number can still ask for one it has no business asking
 * for, and the only place that can be caught is here.
 */
class CalculadoraAcordoTest {

    private final CalculadoraAcordo calculadora = new CalculadoraAcordo(new AlcadaOficial());

    /** CARTAO, 47 days late: band 2, so 25% in a lump sum. */
    private final PosicaoDivida cartao = new PosicaoDivida(
            "CT-00231907", Carteira.CARTAO, 47,
            new BigDecimal("1890.55"), new BigDecimal("212.33"), new BigDecimal("2102.88"));

    @Test
    void appliesDiscountAndSplitsIntoInstallments() {
        SimulacaoAcordo simulacao = calculadora.simular(cartao, new BigDecimal("25"), 4);

        assertThat(simulacao.valorComDesconto()).isEqualByComparingTo("1577.16");
        assertThat(simulacao.valorDaParcela()).isEqualByComparingTo("394.29");
        assertThat(simulacao.faixa()).isEqualTo(FaixaAtraso.F2_31_A_90);
        assertThat(simulacao.saldoAtualizado()).isEqualByComparingTo("2102.88");
    }

    @Test
    void reportsTheCeilingForThePlanNotJustForALumpSum() {
        // 25% is exactly the lump-sum authority for CARTAO in band 2. Spread over
        // four instalments, section 5 takes ten points off it, so the same 25%
        // that an operator could grant outright now needs a supervisor.
        SimulacaoAcordo simulacao = calculadora.simular(cartao, new BigDecimal("25"), 4);

        assertThat(simulacao.percentualMaximoPolitica()).isEqualByComparingTo("15");
        assertThat(simulacao.dentroDaAlcadaDoOperador()).isFalse();
        assertThat(simulacao.aprovacao()).isEqualTo(AprovacaoNecessaria.SUPERVISOR);
    }

    @Test
    void grantsWithinTheOperatorsOwnAuthority() {
        SimulacaoAcordo simulacao = calculadora.simular(cartao, new BigDecimal("15"), 1);

        assertThat(simulacao.valorComDesconto()).isEqualByComparingTo("1787.45");
        assertThat(simulacao.percentualMaximoPolitica()).isEqualByComparingTo("25");
        assertThat(simulacao.dentroDaAlcadaDoOperador()).isTrue();
        assertThat(simulacao.aprovacao()).isEqualTo(AprovacaoNecessaria.NENHUMA);
        assertThat(simulacao.restricoes()).isEmpty();
        assertThat(simulacao.entradaMinima()).isEqualByComparingTo("0");
    }

    @Test
    void sendsAnythingAboveSeventyPercentToTheManagerEvenWhenTheCarteiraAllowsIt() {
        // CARTAO past 360 days allows 85%, so the band check alone would let this
        // through on the operator's own authority. The 70% rule in section 4 is
        // absolute and has to be checked first.
        PosicaoDivida writeOff = new PosicaoDivida(
                "CT-00900001", Carteira.CARTAO, 400,
                new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("1500.00"));

        SimulacaoAcordo simulacao = calculadora.simular(writeOff, new BigDecimal("80"), 1);

        assertThat(simulacao.percentualMaximoPolitica()).isEqualByComparingTo("85");
        assertThat(simulacao.aprovacao()).isEqualTo(AprovacaoNecessaria.GERENTE_RECUPERACAO);
        assertThat(simulacao.dentroDaAlcadaDoOperador()).isFalse();
    }

    @Test
    void flagsAPlanLongerThanThePolicyAllows() {
        SimulacaoAcordo simulacao = calculadora.simular(cartao, new BigDecimal("10"), 30);

        assertThat(simulacao.aprovacao()).isEqualTo(AprovacaoNecessaria.GERENTE_RECUPERACAO);
        assertThat(simulacao.restricoes()).anyMatch(r -> r.contains("24 parcelas"));
    }

    @Test
    void flagsAnInstalmentBelowTheFloorAndSaysHowManyWouldFit() {
        // 50% off 2102.88 is 1051.44. Over 24 instalments that is R$ 43.81 each,
        // under the R$ 80 minimum in section 5. Thirteen is the most that fits.
        SimulacaoAcordo simulacao = calculadora.simular(cartao, new BigDecimal("50"), 24);

        assertThat(simulacao.valorDaParcela()).isEqualByComparingTo("43.81");
        assertThat(simulacao.restricoes()).anyMatch(r -> r.contains("80.00") && r.contains("13"));
    }

    @Test
    void requiresADownPaymentOnLongPlans() {
        SimulacaoAcordo semEntrada = calculadora.simular(cartao, new BigDecimal("10"), 12);
        SimulacaoAcordo comEntrada = calculadora.simular(cartao, new BigDecimal("50"), 13);

        assertThat(semEntrada.entradaMinima()).isEqualByComparingTo("0");
        assertThat(comEntrada.entradaMinima()).isEqualByComparingTo("105.14");
    }

    @Test
    void warnsThatAVehicleContractMayNeedTheCollateralTeam() {
        PosicaoDivida veiculo = new PosicaoDivida(
                "CT-00245110", Carteira.CDC_VEICULO, 95,
                new BigDecimal("18750.00"), new BigDecimal("3112.50"), new BigDecimal("21862.50"));

        SimulacaoAcordo simulacao = calculadora.simular(veiculo, new BigDecimal("10"), 6);

        assertThat(simulacao.restricoes()).anyMatch(r -> r.contains("busca e apreensao"));
    }

    @Test
    void rejectsDiscountAboveOneHundredPercent() {
        assertThatThrownBy(() -> calculadora.simular(cartao, new BigDecimal("120"), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroInstallments() {
        assertThatThrownBy(() -> calculadora.simular(cartao, BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingArguments() {
        assertThatThrownBy(() -> calculadora.simular(cartao, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculadora.simular(null, BigDecimal.TEN, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
