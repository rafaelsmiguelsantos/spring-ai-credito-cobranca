package br.com.cronicas.credito.atendimento;

/**
 * What the customer signalled they intend to do. Drives the next step of the
 * collections workflow, so it must be a value the workflow already knows.
 */
public enum IntencaoDevedor {
    QUITAR_A_VISTA,
    PARCELAR,
    PEDIR_PRAZO,
    CONTESTAR,
    SEM_CONDICOES,
    RECUSA_NEGOCIAR,
    INDEFINIDA
}
