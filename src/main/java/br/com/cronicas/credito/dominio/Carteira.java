package br.com.cronicas.credito.dominio;

/**
 * The four credit portfolios. A closed vocabulary, for the same reason
 * {@code MotivoInadimplencia} is one: almost every rule in the manual varies by
 * carteira, so a typo in a carteira name is a wrong answer, not a 404.
 *
 * <p>This being an enum is also what makes the retrieval filter safe. The filter
 * expression for pgvector is built by string formatting, and the carteira used to
 * arrive at it comes straight off an HTTP query parameter. A {@code String} there
 * is a filter-injection hole; an enum closes it at the edge, and Spring converts
 * an unknown value into a 400 before any of this code runs.
 */
public enum Carteira {

    /** Payroll-deducted credit. Lowest loss risk, so the tightest discount authority. */
    CONSIGNADO,

    /** Credit card revolving and instalment debt. Fast deterioration, widest authority. */
    CARTAO,

    /** Vehicle-secured consumer credit. Negotiation competes with repossession. */
    CDC_VEICULO,

    /** Unsecured personal loan. Intermediate authority. */
    PESSOAL
}
