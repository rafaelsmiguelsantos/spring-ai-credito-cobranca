package br.com.cronicas.credito.divida;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The multi-turn assistant, over HTTP.
 *
 * <p>{@link AssistenteNegociacao} existed before this controller did and nothing
 * called it: the one piece of the first article that actually demonstrates tools
 * and memory working together was unreachable, and therefore also untested
 * end to end. A service with no caller is not a feature, it is a snippet.
 *
 * <p>About {@code conversationId}: here it comes off the URL, which is fine for
 * an article and wrong for production. It is the key the chat memory is stored
 * under, so a client that guesses someone else's id reads someone else's
 * conversation — including the contract numbers in it. In a real deployment this
 * is derived from the authenticated analyst, never accepted from the request.
 */
@RestController
@RequestMapping("/assistente")
public class AssistenteController {

    private final AssistenteNegociacao assistente;

    public AssistenteController(AssistenteNegociacao assistente) {
        this.assistente = assistente;
    }

    public record PerguntaRequest(
            @NotBlank @Size(max = 2_000) String pergunta) {
    }

    public record PerguntaResposta(String conversationId, String resposta) {
    }

    @PostMapping("/{conversationId}")
    public ResponseEntity<PerguntaResposta> perguntar(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String conversationId,
            @Valid @RequestBody PerguntaRequest request) {

        String resposta = assistente.perguntar(conversationId, request.pergunta());
        return ResponseEntity.ok(new PerguntaResposta(conversationId, resposta));
    }
}
