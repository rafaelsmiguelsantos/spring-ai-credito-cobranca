package br.com.cronicas.credito.politica;

import br.com.cronicas.credito.dominio.Carteira;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Answers a question against the collections manual.
 *
 * <p>The whole of RAG here is one advisor. That is the point worth noticing:
 * retrieval is not a separate pipeline the application orchestrates, it is a link
 * in the chain around the model call, and it composes with the memory advisor and
 * the audit advisor without any of them knowing about each other.
 */
@Service
public class PoliticaQueryService {

    private static final String SYSTEM = """
            Voce responde duvidas sobre o manual de cobranca de uma financeira.

            Regras:
            - Responda exclusivamente com base no contexto fornecido. Nao use conhecimento geral.
            - Se a resposta nao estiver no contexto, retorne respondido=false e diga o que falta.
              Nao tente deduzir a partir de outra carteira ou de outra faixa de atraso.
            - Sempre preencha secaoCitada com o titulo exato da secao usada.
            - Confira a faixa de atraso antes de responder: 1 a 30, 31 a 90, 91 a 180,
              181 a 360, acima de 360. Um contrato com 180 dias esta na faixa de 91 a 180.
            """;

    /**
     * The question is a parameter, never the template. The user text goes through
     * template rendering before it reaches the model, so a question that happens
     * to contain a brace — pasted from a ticket, a JSON payload, a log line —
     * fails the render rather than the retrieval, and the stack trace points at
     * the template engine instead of at the input.
     */
    private static final String USER = "{pergunta}";

    private final ChatClient chatClient;

    public PoliticaQueryService(ChatClient.Builder builder,
                                VectorStore vectorStore,
                                PoliticaProperties properties) {

        var retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(properties.similarityThreshold())
                .topK(properties.topK())
                .build();

        // allowEmptyContext=false: when nothing is retrieved, the model is told to
        // say so rather than answer from whatever it happens to know. An empty
        // retrieval is the single most common source of a confident wrong answer.
        var augmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .build();

        var ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(augmenter)
                .build();

        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .defaultOptions(ChatOptions.builder().temperature(0.0d))
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    /**
     * @param carteira optional. When present, retrieval is restricted to sections
     *                 tagged with that carteira plus the sections tagged
     *                 {@link MetadadosPolitica#CARTEIRA_GERAL}. Similarity alone
     *                 does not reliably separate "CARTAO" from "CONSIGNADO": the
     *                 four alcada sections are near-identical text differing only
     *                 in a noun and five numbers.
     *                 <p>Being an enum is what makes the formatted filter
     *                 expression safe. The same string built from a raw request
     *                 parameter is a filter-injection hole, and one that reads as
     *                 harmless because the value "is just a portfolio name".
     */
    public RespostaPolitica perguntar(String pergunta, Carteira carteira) {
        Assert.hasText(pergunta, "pergunta must not be blank");

        var spec = chatClient.prompt().user(u -> u.text(USER).param("pergunta", pergunta));

        if (carteira != null) {
            String filter = "%1$s == '%2$s' || %1$s == '%3$s'".formatted(
                    MetadadosPolitica.CARTEIRA, carteira.name(), MetadadosPolitica.CARTEIRA_GERAL);
            spec = spec.advisors(a -> a.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filter));
        }

        return spec.call().entity(RespostaPolitica.class);
    }
}
