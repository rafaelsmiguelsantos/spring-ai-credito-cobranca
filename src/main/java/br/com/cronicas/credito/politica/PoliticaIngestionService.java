package br.com.cronicas.credito.politica;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * The ETL half of RAG: read, transform, write.
 *
 * <p>The three interfaces behind it are the same shape as Spring Batch:
 * {@code DocumentReader} is a {@code Supplier<List<Document>>},
 * {@code DocumentTransformer} is a {@code Function<List<Document>, List<Document>>},
 * and {@code VectorStore} is a {@code Consumer<List<Document>>}. Ingestion is a
 * pipeline, and each stage is replaceable.
 *
 * <p>The delete is the part that is easy to leave out and expensive to leave out.
 * {@code VectorStore.add} assigns a fresh id to every document, so it appends
 * rather than replaces: ingesting the same manual twice does not refresh it, it
 * duplicates it. With {@code topK} of four and five copies of the same section in
 * the store, retrieval returns the same chunk four times, the real context never
 * arrives, and the symptom is a model that has started "forgetting" rules it
 * answered correctly last week. Re-ingestion is the normal case — a manual gets
 * revised — so ingestion has to be idempotent, and {@code fonte} is the handle
 * that lets a run replace exactly what it wrote before.
 */
@Service
public class PoliticaIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PoliticaIngestionService.class);

    public record IngestaoResultado(String fonte, int secoesIngeridas) {
    }

    private final VectorStore vectorStore;

    public PoliticaIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public IngestaoResultado ingest(Resource manual) {
        ManualCobrancaReader reader = new ManualCobrancaReader(manual);
        String fonte = reader.fonte();
        List<Document> sections = reader.read();

        log.info("Ingerindo {} secoes de {}", sections.size(), fonte);
        sections.forEach(d -> log.debug("secao='{}' carteira={} chars={}",
                d.getMetadata().get(MetadadosPolitica.SECAO),
                d.getMetadata().get(MetadadosPolitica.CARTEIRA),
                d.getText() == null ? 0 : d.getText().length()));

        // The filter value comes from a file name on the classpath, not from a
        // request, but it is quoted the same way regardless: a filter expression
        // built by string formatting is a place where untrusted input becomes
        // query syntax, and the habit is cheaper than the audit.
        vectorStore.delete("%s == '%s'".formatted(MetadadosPolitica.FONTE, fonte.replace("'", "''")));
        vectorStore.write(sections);

        return new IngestaoResultado(fonte, sections.size());
    }
}
