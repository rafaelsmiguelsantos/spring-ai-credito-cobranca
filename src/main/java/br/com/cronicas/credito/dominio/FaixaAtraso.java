package br.com.cronicas.credito.dominio;

/**
 * The arrears bands used throughout the manual.
 *
 * <p>Extracted from the authority table and given its own type because the band
 * boundary is where this domain goes wrong most often. "180 dias" and "181 dias"
 * are different bands and, on CARTAO, a twenty-point difference in discount. A
 * model asked to classify the band gets it right most of the time, which is the
 * worst possible failure rate for arithmetic: frequent enough to trust, rare
 * enough that nobody checks.
 *
 * <p>So the band is never inferred. It is computed here, from a number, by code
 * that a unit test pins at every boundary.
 */
public enum FaixaAtraso {

    F1_ATE_30("1 a 30 dias"),
    F2_31_A_90("31 a 90 dias"),
    F3_91_A_180("91 a 180 dias"),
    F4_181_A_360("181 a 360 dias"),
    F5_ACIMA_360("acima de 360 dias");

    private final String descricao;

    FaixaAtraso(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    /**
     * @param diasEmAtraso days past due, zero or more. Zero falls in the first
     *                     band, which carries no discount in any carteira, so a
     *                     contract that is not actually overdue cannot be
     *                     discounted by accident.
     * @throws IllegalArgumentException if {@code diasEmAtraso} is negative
     */
    public static FaixaAtraso de(int diasEmAtraso) {
        if (diasEmAtraso < 0) {
            throw new IllegalArgumentException("diasEmAtraso nao pode ser negativo: " + diasEmAtraso);
        }
        if (diasEmAtraso <= 30) {
            return F1_ATE_30;
        }
        if (diasEmAtraso <= 90) {
            return F2_31_A_90;
        }
        if (diasEmAtraso <= 180) {
            return F3_91_A_180;
        }
        if (diasEmAtraso <= 360) {
            return F4_181_A_360;
        }
        return F5_ACIMA_360;
    }
}
