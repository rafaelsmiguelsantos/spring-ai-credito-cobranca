package br.com.cronicas.credito.politica;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * The retrieval knobs, out of the constructors and into configuration.
 *
 * <p>{@code topK} and {@code similarityThreshold} are the two numbers you
 * actually end up tuning in a RAG pipeline, and they are worth tuning against a
 * fixed set of questions rather than against the last complaint. A threshold set
 * too low retrieves noise and the model answers from it; set too high it
 * retrieves nothing, which — with {@code allowEmptyContext=false} — at least
 * fails loudly. Hard-coded in a constructor, neither value can be changed per
 * environment or swept in a test.
 *
 * @param manual              where the manual lives. Also removes a classpath
 *                            string that was duplicated between the controller
 *                            and the integration test, with nothing keeping the
 *                            two in step across a version bump of the file.
 * @param topK                how many sections to retrieve. Four fits a policy
 *                            question: an answer that needs more than four
 *                            sections of a manual is usually a question that
 *                            needs splitting.
 * @param similarityThreshold minimum cosine similarity for a chunk to count as
 *                            relevant
 */
@ConfigurationProperties("credito.politica")
public record PoliticaProperties(

        @DefaultValue("classpath:politica/manual-cobranca-v3.md") Resource manual,

        @DefaultValue("4") int topK,

        @DefaultValue("0.45") double similarityThreshold) {
}
