package br.com.cronicas.credito.divida;

import br.com.cronicas.credito.dominio.Carteira;
import br.com.cronicas.credito.dominio.FaixaAtraso;
import java.math.BigDecimal;

/**
 * What the policy allows for one contract and one plan length.
 *
 * <p>The tool that returns this could have returned a bare number, and that is
 * the version worth not writing. A tool result is read by the model, and {@code 15}
 * on its own carries no indication of what it is 15 of, which band produced it or
 * whether the plan length was taken into account — so the model fills those in,
 * from the conversation, with reasonable guesses. Naming the fields is the
 * cheapest prompt engineering available: the schema is the explanation.
 */
public record AlcadaDoContrato(
        String contrato,
        Carteira carteira,
        int diasEmAtraso,
        FaixaAtraso faixa,
        int parcelas,
        BigDecimal percentualMaximo) {
}
