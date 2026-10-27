package br.com.cronicas.credito.web;

import br.com.cronicas.credito.divida.ContratoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the failures this application actually has into responses a caller can
 * act on.
 *
 * <p>Without this, every one of them is a 500 with a stack trace: a contract that
 * does not exist, a model that timed out, an agent loop that hit its tool budget.
 * They are four different problems with four different correct reactions — fix
 * the request, retry later, do not retry, call a human — and collapsing them into
 * one status throws away the only information the caller needed.
 *
 * <p>The distinction between the two AI exceptions is the one worth keeping.
 * {@link TransientAiException} is a rate limit or a blip, and 503 with a retry is
 * the right answer. {@link NonTransientAiException} is a bad request or a
 * rejected key, and retrying it just spends money on the same failure.
 *
 * <p>Validation failures are deliberately absent: Spring Boot already renders
 * them as {@code ProblemDetail} with a 400 and field-level detail, and a handler
 * here would only make that worse.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ContratoNaoEncontradoException.class)
    ProblemDetail contratoNaoEncontrado(ContratoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Contrato nao encontrado", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail argumentoInvalido(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", e.getMessage());
    }

    @ExceptionHandler(ToolCallLimitExceededException.class)
    ProblemDetail limiteDeFerramentas(ToolCallLimitExceededException e) {
        log.warn("ai.tool.limite_excedido {}", e.getMessage());
        return problema(HttpStatus.UNPROCESSABLE_CONTENT,
                "O assistente nao concluiu a resposta",
                "O limite de chamadas de ferramenta foi atingido. Reformule a pergunta "
                        + "ou divida em passos menores.");
    }

    @ExceptionHandler(TransientAiException.class)
    ProblemDetail falhaTemporaria(TransientAiException e) {
        log.warn("ai.falha.transitoria {}", e.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE,
                "Servico de IA indisponivel",
                "Falha temporaria ao consultar o modelo. Tente novamente.");
    }

    @ExceptionHandler(NonTransientAiException.class)
    ProblemDetail falhaPermanente(NonTransientAiException e) {
        log.error("ai.falha.permanente {}", e.getMessage());
        return problema(HttpStatus.BAD_GATEWAY,
                "Falha ao consultar o modelo",
                "A chamada ao modelo foi rejeitada. Nao repita a requisicao sem alterar a entrada.");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
