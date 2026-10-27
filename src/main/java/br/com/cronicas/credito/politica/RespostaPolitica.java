package br.com.cronicas.credito.politica;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;

/**
 * A policy answer that can be checked.
 *
 * <p>A free-text answer is unauditable: you cannot assert on it, you cannot
 * compare it to the ledger, and you cannot tell a hallucinated percentage from a
 * correct one. Forcing the answer into this shape makes the number a field, and a
 * field can be validated against the domain.
 */
@JsonPropertyOrder({"secaoCitada", "resposta", "percentualMaximo", "respondido"})
public record RespostaPolitica(

        @JsonPropertyDescription("Titulo exato da secao do manual que sustenta a resposta. "
                + "Vazio se a resposta nao foi encontrada no contexto")
        String secaoCitada,

        @JsonPropertyDescription("Resposta objetiva, em uma ou duas frases")
        String resposta,

        @JsonPropertyDescription("Percentual maximo de desconto, quando a pergunta for sobre alcada. "
                + "Null se a pergunta nao for sobre percentual ou se nao houver essa informacao no contexto")
        BigDecimal percentualMaximo,

        @JsonPropertyDescription("False se a informacao nao estiver no contexto fornecido")
        boolean respondido) {
}
