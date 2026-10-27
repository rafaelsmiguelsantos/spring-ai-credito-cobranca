package br.com.cronicas.credito.divida;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import java.math.BigDecimal;
import java.util.List;

/**
 * The result of simulating one agreement.
 *
 * <p>This record grew well past "the two numbers the model asked for", and
 * deliberately. A simulation that reports only {@code valorDaParcela} is a number
 * with no standing: the analyst cannot tell whether it is within their authority,
 * the model cannot tell whether proposing it was reasonable, and the audit trail
 * cannot tell whether it should have been offered at all.
 *
 * <p>So the policy context travels with the number: which band the contract is
 * in, what the ceiling was for that band and plan length, and who has to approve
 * what was asked for. The model is free to propose; it is not free to decide, and
 * the shape of this record is what makes that difference visible.
 *
 * @param percentualSolicitado      the discount that was asked for, not the one
 *                                  the policy allows
 * @param percentualMaximoPolitica  the ceiling for this carteira, band and number
 *                                  of instalments, from {@code AlcadaOficial}
 * @param dentroDaAlcadaDoOperador  true when the operator can grant this alone
 * @param restricoes                conditions the manual attaches to this
 *                                  agreement that no amount of arithmetic can
 *                                  settle, such as a minimum instalment the plan
 *                                  breaches or a repossession suit to check
 */
public record SimulacaoAcordo(
        String contrato,
        Carteira carteira,
        int diasEmAtraso,
        FaixaAtraso faixa,
        BigDecimal saldoAtualizado,
        int parcelas,
        BigDecimal percentualSolicitado,
        BigDecimal percentualMaximoPolitica,
        boolean dentroDaAlcadaDoOperador,
        AprovacaoNecessaria aprovacao,
        BigDecimal valorComDesconto,
        BigDecimal valorDaParcela,
        BigDecimal entradaMinima,
        List<String> restricoes) {

    public SimulacaoAcordo {
        restricoes = List.copyOf(restricoes);
    }
}
