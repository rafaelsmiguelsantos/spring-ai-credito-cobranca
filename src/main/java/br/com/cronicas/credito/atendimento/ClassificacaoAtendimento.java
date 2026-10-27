package br.com.cronicas.credito.atendimento;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The typed result of reading one collections conversation.
 *
 * <p>This record IS the contract with the model. Spring AI derives a JSON schema
 * from it, injects the schema into the prompt, and deserializes the answer back
 * into this type. Every field description below ends up in that schema, so they
 * are prompt engineering, not documentation.
 *
 * <p>{@code justificativa} is deliberately last: asking for the conclusion first
 * and the reasoning after tends to produce a rationalization of a snap answer.
 */
@JsonPropertyOrder({"justificativa", "motivo", "intencao", "promessa", "indicioDeVulnerabilidade"})
public record ClassificacaoAtendimento(

        @JsonPropertyDescription("Uma frase curta citando o trecho do atendimento que sustenta a classificacao")
        String justificativa,

        @JsonPropertyDescription("Motivo declarado pelo cliente para o nao pagamento")
        MotivoInadimplencia motivo,

        @JsonPropertyDescription("O que o cliente sinalizou que pretende fazer")
        IntencaoDevedor intencao,

        @JsonPropertyDescription("Promessa de pagamento, se o cliente fez uma. Null caso contrario")
        PromessaPagamento promessa,

        @JsonPropertyDescription("True se houver indicio de superendividamento ou vulnerabilidade "
                + "que exija tratamento diferenciado por forca da Lei 14.181/2020")
        boolean indicioDeVulnerabilidade) {
}
