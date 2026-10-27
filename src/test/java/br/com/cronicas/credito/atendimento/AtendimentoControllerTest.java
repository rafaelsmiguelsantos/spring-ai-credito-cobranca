package br.com.cronicas.credito.atendimento;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.cronicas.credito.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The web layer, with the model mocked out.
 *
 * <p>The assertion that earns this test its place is {@code verifyNoInteractions}.
 * Validation on an AI endpoint is not about returning a tidy 400 — it is about a
 * malformed request never becoming a paid model call. An empty transcript sent to
 * a classifier does not fail; it returns a confident classification of nothing,
 * having cost real tokens, and the endpoint is a free amplifier for anyone who
 * finds it.
 *
 * <p>Built standalone rather than with a context slice, so it stays a unit test:
 * no Spring context, no provider autoconfiguration, no model server.
 */
class AtendimentoControllerTest {

    private final AtendimentoClassifier classifier = mock(AtendimentoClassifier.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AtendimentoController(classifier))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void classifiesAValidTranscript() throws Exception {
        when(classifier.classify(anyString())).thenReturn(new ClassificacaoAtendimento(
                "Cliente disse que foi desligado em agosto",
                MotivoInadimplencia.DESEMPREGO,
                IntencaoDevedor.PEDIR_PRAZO,
                null,
                false));

        mockMvc.perform(post("/atendimentos/classificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcricao\":\"Cliente: fui desligado em agosto.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivo").value("DESEMPREGO"))
                .andExpect(jsonPath("$.intencao").value("PEDIR_PRAZO"))
                .andExpect(jsonPath("$.indicioDeVulnerabilidade").value(false));

        verify(classifier).classify("Cliente: fui desligado em agosto.");
    }

    @Test
    void rejectsABlankTranscriptWithoutCallingTheModel() throws Exception {
        mockMvc.perform(post("/atendimentos/classificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcricao\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(classifier);
    }

    @Test
    void rejectsAnOversizedTranscriptWithoutCallingTheModel() throws Exception {
        String gigante = "a".repeat(20_001);

        mockMvc.perform(post("/atendimentos/classificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcricao\":\"" + gigante + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(classifier);
    }

    @Test
    void rejectsAMissingTranscriptWithoutCallingTheModel() throws Exception {
        mockMvc.perform(post("/atendimentos/classificacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(classifier);
    }
}
