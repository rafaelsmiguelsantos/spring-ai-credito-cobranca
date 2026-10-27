package br.com.cronicas.credito.politica;

import br.com.cronicas.credito.dominio.Carteira;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/politica")
public class PoliticaController {

    private final PoliticaIngestionService ingestion;
    private final ConsultaAlcadaService consulta;
    private final PoliticaProperties properties;

    public PoliticaController(PoliticaIngestionService ingestion,
                              ConsultaAlcadaService consulta,
                              PoliticaProperties properties) {
        this.ingestion = ingestion;
        this.consulta = consulta;
        this.properties = properties;
    }

    @PostMapping("/ingestao")
    public ResponseEntity<PoliticaIngestionService.IngestaoResultado> ingerir() {
        return ResponseEntity.ok(ingestion.ingest(properties.manual()));
    }

    /**
     * {@code carteira} is a {@link Carteira} rather than a {@code String}, and
     * that is a security boundary, not a tidiness preference. The value reaches a
     * vector-store filter expression that is built by string formatting; bound as
     * an enum, an unknown value is a 400 from Spring before any of this code
     * runs, and a crafted one cannot be a value at all.
     */
    @GetMapping("/consulta")
    public ResponseEntity<ConsultaAlcadaService.ConsultaResultado> consultar(
            @RequestParam @NotBlank @Size(max = 500) String pergunta,
            @RequestParam Carteira carteira,
            @RequestParam @Min(0) @Max(36_500) int diasEmAtraso) {

        return ResponseEntity.ok(consulta.consultar(pergunta, carteira, diasEmAtraso));
    }
}
