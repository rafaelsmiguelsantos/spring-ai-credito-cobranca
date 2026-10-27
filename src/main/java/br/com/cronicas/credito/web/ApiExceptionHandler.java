package br.com.cronicas.credito.web;

import br.com.cronicas.credito.divida.ContratoNaoEncontradoException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallLimitExceededException;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
 * <p>{@link ResourceAccessException} is here because of what actually happens
 * when the model server is simply not running. The first version of this class
 * handled only the two Spring AI exceptions, on the assumption that the
 * framework wraps transport failures into them. It does not: once the retry
 * budget is spent, the raw {@code ResourceAccessException} from the HTTP client
 * propagates, so a dead Ollama produced a 500 and a stack trace rather than the
 * 503 this class exists to produce. Guessing which exception a dependency throws
 * is how you end up with handlers that never fire.
 *
 * <p>Validation failures are mostly absent, because Spring Boot already renders
 * them with a 400 and field-level detail. The one exception is
 * {@link MethodArgumentTypeMismatchException}: its cause is an
 * {@code IllegalArgumentException} whose message is
 * "No enum constant br.com.cronicas.credito.dominio.Carteira.FOO", and Spring
 * matches handlers against the cause as well as the exception itself, so the
 * generic handler below was echoing an internal class name back to the caller.
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroComTipoErrado(MethodArgumentTypeMismatchException e) {
        Class<?> tipo = e.getRequiredType();
        String aceitos = tipo != null && tipo.isEnum()
                ? " Valores aceitos: %s.".formatted(Arrays.stream(tipo.getEnumConstants())
                        .map(String::valueOf).collect(Collectors.joining(", ")))
                : "";

        return problema(HttpStatus.BAD_REQUEST, "Parametro invalido",
                "O parametro '%s' recebeu um valor invalido.%s".formatted(e.getName(), aceitos));
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

    /**
     * The model server could not be reached at all: refused connection, DNS
     * failure, or a read timeout. Transient by nature, and the message says
     * nothing about the host or the port, because the caller cannot act on our
     * topology and an error body is not the place to publish it.
     */
    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail modeloInacessivel(ResourceAccessException e) {
        log.error("ai.falha.transporte {}", e.getMessage());
        return problema(HttpStatus.SERVICE_UNAVAILABLE,
                "Servico de IA indisponivel",
                "Nao foi possivel alcancar o modelo. Tente novamente em instantes.");
    }

    /** The model answered, with an error status of its own. */
    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail modeloRespondeuErro(RestClientResponseException e) {
        log.error("ai.falha.resposta status={} {}", e.getStatusCode(), e.getMessage());
        return problema(HttpStatus.BAD_GATEWAY,
                "Falha ao consultar o modelo",
                "O modelo respondeu com erro. Nao repita a requisicao sem alterar a entrada.");
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
