package br.com.cronicas.credito.divida;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.cronicas.credito.TestcontainersConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Tools and memory, end to end.
 *
 * <p>This is the piece of the first article with no test at all until now, which
 * is awkward, because it is the piece with the most moving parts: the model has
 * to decide to call a tool, call it with the right arguments, read the result and
 * carry the context into the next turn.
 *
 * <p>The assertions are deliberately about provenance rather than wording. There
 * is no point asserting on a phrasing a model will produce differently tomorrow;
 * what has to hold is that the numbers in the answer came from the ledger. A
 * balance of 2102.88 does not appear in any prompt — the only way into the reply
 * is a tool call, so finding it there is evidence the loop worked, and finding a
 * different number is evidence the model did arithmetic it was told not to do.
 *
 * <p>Requires Ollama running locally with a tool-calling model pulled.
 */
@SpringBootTest
@ActiveProfiles("ollama")
@Import(TestcontainersConfig.class)
class AssistenteNegociacaoIT {

    @Autowired
    AssistenteNegociacao assistente;

    @Test
    void takesEveryNumberFromTheLedger() {
        String resposta = assistente.perguntar(novaConversa(),
                "Qual a posicao atual do contrato CT-00231907?");

        assertThat(resposta).containsPattern("2\\.?102[.,]88");
    }

    @Test
    void carriesTheContractAcrossTurns() {
        String conversa = novaConversa();

        assistente.perguntar(conversa, "Qual a posicao atual do contrato CT-00231907?");

        // The contract number is not repeated. Answering this at all requires the
        // history, and answering it correctly requires a second tool call with an
        // argument the model had to remember.
        String resposta = assistente.perguntar(conversa,
                "E quanto ficaria com 20 por cento de desconto em 4 parcelas?");

        // 20% off 2102.88 is 1682.30, or 420.58 per instalment. Either figure is
        // proof the simulation ran; neither can be guessed.
        assertThat(resposta).containsPattern("1\\.?682[.,]30|420[.,]58");
    }

    @Test
    void doesNotInventAContractThatIsNotInTheLedger() {
        String resposta = assistente.perguntar(novaConversa(),
                "Qual a posicao atual do contrato CT-99999999?");

        assertThat(resposta).isNotBlank();
        // Whatever it says, it must not have produced a balance for a contract
        // that does not exist.
        assertThat(resposta).doesNotContainPattern("R\\$\\s*\\d");
    }

    private String novaConversa() {
        return UUID.randomUUID().toString();
    }
}
