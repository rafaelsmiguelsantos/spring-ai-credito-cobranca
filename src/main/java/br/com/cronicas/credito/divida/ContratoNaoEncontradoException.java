package br.com.cronicas.credito.divida;

/**
 * Thrown when the model asks about a contract the ledger does not have.
 *
 * <p>A top-level type rather than a nested one because two very different callers
 * need to react to it: the HTTP layer turns it into a 404, and the tool-calling
 * machinery turns it into a message the model reads and can recover from by
 * asking the analyst for the right contract number. Both need to name it.
 */
public class ContratoNaoEncontradoException extends RuntimeException {

    private final String contrato;

    public ContratoNaoEncontradoException(String contrato) {
        super("Contrato nao encontrado: " + contrato);
        this.contrato = contrato;
    }

    public String contrato() {
        return contrato;
    }
}
