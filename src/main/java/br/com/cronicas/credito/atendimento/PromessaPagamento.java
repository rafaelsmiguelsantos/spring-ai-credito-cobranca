package br.com.cronicas.credito.atendimento;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A payment promise extracted from the conversation.
 *
 * <p>Note what is NOT here: the outstanding balance. The model reports what the
 * customer said they would pay, never what they owe. The amount owed comes from
 * the ledger.
 */
public record PromessaPagamento(

        @JsonPropertyDescription("Valor que o cliente se comprometeu a pagar, exatamente como dito por ele")
        BigDecimal valor,

        @JsonPropertyDescription("Data prometida no formato ISO-8601 (yyyy-MM-dd). Null se o cliente nao deu data")
        LocalDate data) {
}
