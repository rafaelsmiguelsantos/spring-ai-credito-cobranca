package br.com.cronicas.credito.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Writes an audit record for every model call.
 *
 * <p>An advisor is to a model call what a {@code HandlerInterceptor} is to an HTTP
 * request: it wraps the call, sees the request on the way in and the response on
 * the way out, and hands control to the next link in the chain.
 *
 * <p>In a regulated domain this is not optional plumbing. If a customer disputes
 * what they were told, the question "what exactly did the model see and say" has
 * to have an answer, and the answer cannot be reconstructed after the fact.
 *
 * <p>The order matters. {@link ToolCallingAdvisor#DEFAULT_ORDER} is
 * {@code HIGHEST_PRECEDENCE + 300} and it loops until the model stops asking for
 * tools. Running before it, as here, audits the whole exchange once. Running
 * after it would produce one record per tool iteration.
 *
 * <p>Failures are audited too. An audit trail that only records successes answers
 * the easy half of the question: a call that timed out after the customer was
 * already on the line is exactly the one someone will ask about later.
 */
@Component
public class AuditTrailAdvisor implements CallAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailAdvisor.class);

    @Override
    public String getName() {
        return "auditTrailAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startedAt = System.nanoTime();
        int promptChars = request.prompt().getContents().length();

        try {
            ChatClientResponse response = chain.nextCall(request);
            record(response, promptChars, elapsedMsSince(startedAt));
            return response;
        }
        catch (RuntimeException e) {
            // In production this goes to an append-only store, not to a log file.
            log.error("ai.audit outcome=error elapsedMs={} promptChars={} exception={} message={}",
                    elapsedMsSince(startedAt), promptChars, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private void record(ChatClientResponse response, int promptChars, long elapsedMs) {
        Usage usage = usageOf(response);

        log.info("ai.audit outcome=ok model={} elapsedMs={} promptChars={} responseChars={} "
                        + "promptTokens={} completionTokens={} finishReason={}",
                modelOf(response),
                elapsedMs,
                promptChars,
                textOf(response).length(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                finishReasonOf(response));
    }

    private long elapsedMsSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String modelOf(ChatClientResponse response) {
        ChatResponseMetadata metadata = metadataOf(response);
        return metadata == null ? "unknown" : metadata.getModel();
    }

    private Usage usageOf(ChatClientResponse response) {
        ChatResponseMetadata metadata = metadataOf(response);
        return metadata == null ? null : metadata.getUsage();
    }

    private ChatResponseMetadata metadataOf(ChatClientResponse response) {
        return response.chatResponse() == null ? null : response.chatResponse().getMetadata();
    }

    private String finishReasonOf(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getMetadata() == null) {
            return null;
        }
        return response.chatResponse().getResult().getMetadata().getFinishReason();
    }

    private String textOf(ChatClientResponse response) {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return "";
        }
        String text = response.chatResponse().getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
