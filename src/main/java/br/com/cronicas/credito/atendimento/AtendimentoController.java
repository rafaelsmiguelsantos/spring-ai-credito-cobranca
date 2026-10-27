package br.com.cronicas.credito.atendimento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/atendimentos")
public class AtendimentoController {

    private final AtendimentoClassifier classifier;

    public AtendimentoController(AtendimentoClassifier classifier) {
        this.classifier = classifier;
    }

    /**
     * @param transcricao the bound on the size is not ceremony. Every character
     *                    here becomes tokens in a priced request, and an endpoint
     *                    that forwards an unbounded body to a model is a cost
     *                    amplifier with an HTTP interface. Twenty thousand
     *                    characters is a long call; anything beyond it is a
     *                    mistake or an attack, and neither deserves a model call.
     */
    public record ClassificarRequest(
            @NotBlank @Size(max = 20_000) String transcricao) {
    }

    @PostMapping("/classificacao")
    public ResponseEntity<ClassificacaoAtendimento> classificar(
            @Valid @RequestBody ClassificarRequest request) {

        return ResponseEntity.ok(classifier.classify(request.transcricao()));
    }
}
