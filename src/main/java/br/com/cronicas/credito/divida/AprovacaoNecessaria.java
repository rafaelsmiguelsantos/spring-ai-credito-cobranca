package br.com.cronicas.credito.divida;

/**
 * Who has to sign off on a proposed agreement, per section 4 of the manual.
 *
 * <p>The point of returning this instead of refusing the simulation: an analyst
 * asking for a discount above their authority is doing their job, not misbehaving.
 * The useful answer is "that costs you a supervisor", not an error.
 */
public enum AprovacaoNecessaria {

    /** Within the operator's own authority for the carteira, band and plan length. */
    NENHUMA,

    /** Above the operator's authority, at or below 70%. */
    SUPERVISOR,

    /** Above 70% in any carteira, or more than 24 instalments. */
    GERENTE_RECUPERACAO
}
