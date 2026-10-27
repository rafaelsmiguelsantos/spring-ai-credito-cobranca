package br.com.cronicas.credito.divida;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * A multi-turn assistant for a collections analyst.
 *
 * <p>Two things are stacked on the same {@link ChatClient}: the tools from
 * {@link PosicaoDividaTools} and a sliding window of conversation history. The
 * audit advisor is a third, and it is not here — it is applied to every
 * {@code ChatClient} in the application by
 * {@code br.com.cronicas.credito.advisor.AuditoriaConfig}, which is where a
 * concern that must never be forgotten belongs.
 *
 * <p>The analyst refines the question across turns ("e se fossem 12 parcelas?"),
 * which is exactly what the memory advisor is for.
 *
 * <p>The window is deliberately short. A long window is not a better memory, it
 * is a larger bill and a longer prompt for the model to lose the thread in, and
 * the history is backed by an injected {@link ChatMemoryRepository} rather than
 * built here: in a single process the autoconfigured in-memory repository is
 * fine, and across several instances it is a lost conversation on every
 * redeploy. Keeping it injected makes that a dependency and configuration
 * change, not a change to this class.
 */
@Service
public class AssistenteNegociacao {

    private static final String SYSTEM = """
            Voce apoia um analista de cobranca. Responda de forma objetiva.

            Regras inegociaveis:
            - Todo numero que voce citar deve vir de uma chamada de ferramenta. Nunca calcule
              de cabeca, nunca estime, nunca arredonde por conta propria.
            - Antes de propor desconto, consulte a alcada maxima do contrato.
            - Ao relatar uma simulacao, informe sempre quem precisa aprovar e as restricoes,
              nao apenas os valores.
            - Se nao houver ferramenta para o que foi pedido, diga que nao tem a informacao.
            - Voce nao efetiva acordos. Voce apenas consulta e simula.
            """;

    /**
     * The question goes in as a template parameter, not as the template itself.
     * Template rendering happens on the user text, so a question containing a
     * brace — a JSON snippet pasted from a ticket, say — would blow up the render
     * rather than reach the model. Free text is always a parameter.
     */
    private static final String USER = "{pergunta}";

    private final ChatClient chatClient;

    public AssistenteNegociacao(ChatClient.Builder builder,
                                PosicaoDividaTools tools,
                                ChatMemoryRepository chatMemoryRepository) {

        ChatMemory memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();

        this.chatClient = builder
                .defaultSystem(SYSTEM)
                .defaultOptions(ChatOptions.builder().temperature(0.1d))
                .defaultTools(tools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
    }

    public String perguntar(String conversationId, String pergunta) {
        Assert.hasText(conversationId, "conversationId must not be blank");
        Assert.hasText(pergunta, "pergunta must not be blank");

        return chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(u -> u.text(USER).param("pergunta", pergunta))
                .call()
                .content();
    }
}
