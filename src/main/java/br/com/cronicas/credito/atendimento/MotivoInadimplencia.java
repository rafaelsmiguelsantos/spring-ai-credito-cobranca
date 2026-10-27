package br.com.cronicas.credito.atendimento;

/**
 * Closed vocabulary for the reason a customer gave for not paying.
 * An enum is what keeps the model from inventing a new category on every call.
 */
public enum MotivoInadimplencia {
    DESEMPREGO,
    REDUCAO_DE_RENDA,
    DESPESA_MEDICA,
    ESQUECIMENTO,
    CONTESTACAO_DE_VALOR,
    FALHA_NO_PAGAMENTO,
    SUPERENDIVIDAMENTO,
    FRAUDE_ALEGADA,
    NAO_INFORMADO
}
