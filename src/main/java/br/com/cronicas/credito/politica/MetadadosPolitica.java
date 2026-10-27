package br.com.cronicas.credito.politica;

import br.com.cronicas.credito.dominio.Carteira;

/**
 * The metadata contract between the reader, the vector store and the retriever.
 *
 * <p>These four keys are written during ingestion and read during retrieval, in
 * different classes, and a typo in either place produces no error — just a filter
 * that silently matches nothing and an answer that looks plausible. Naming them
 * once is the cheapest guard available against the worst failure mode this
 * pipeline has.
 */
public final class MetadadosPolitica {

    /** Exact section heading. Used for the citation the answer has to carry. */
    public static final String SECAO = "secao";

    /** Parent heading, for a subsection. */
    public static final String SECAO_PAI = "secao_pai";

    /** The {@link Carteira} this chunk is about, or {@link #CARTEIRA_GERAL}. */
    public static final String CARTEIRA = "carteira";

    /** Source file, so an ingestion can replace exactly what it loaded before. */
    public static final String FONTE = "fonte";

    /**
     * Sentinel for a section that applies to every carteira.
     *
     * <p>The obvious design is to leave {@code carteira} absent on general
     * sections and retrieve with {@code carteira == 'CARTAO' || !(carteira in
     * [...])}. It does not work, and it fails quietly. The filter becomes SQL
     * against a JSON field that is missing on exactly those rows, {@code NOT IN}
     * over {@code NULL} is {@code NULL} rather than true, and every general
     * section — conduct, vulnerability, agreement breakage, most of the manual —
     * drops out of retrieval while the carteira-specific ones still answer. The
     * pipeline looks like it works because the question that gets tested is an
     * authority question.
     *
     * <p>Tagging every chunk means the filter is two equality checks, which have
     * no null semantics to get wrong.
     */
    public static final String CARTEIRA_GERAL = "GERAL";

    private MetadadosPolitica() {
    }
}
