package br.com.cronicas.credito.divida;

import br.com.cronicas.credito.dominio.FaixaAtraso;
import br.com.cronicas.credito.politica.AlcadaOficial;
import java.math.BigDecimal;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The tools the model is allowed to call.
 *
 * <p>Every method here reads or simulates. None of them writes. Effecting an
 * agreement, registering a write-off and filing a credit bureau entry exist in
 * this system too, and they are deliberately absent from this class: the model
 * proposes, the workflow decides, a human or a deterministic rule executes.
 *
 * <p>The descriptions are not comments. They are the only thing the model reads
 * when deciding whether to call a tool, so they are written for that reader.
 *
 * <p>Errors thrown here are not bugs in the exchange. With Spring AI's default
 * {@code spring.ai.tools.throw-exception-on-error=false}, a
 * {@link ContratoNaoEncontradoException} comes back to the model as a message,
 * which is what lets it recover by asking the analyst for the right contract
 * number instead of failing the whole turn.
 */
@Component
public class PosicaoDividaTools {

    private final DividaRepository repository;
    private final CalculadoraAcordo calculadora;
    private final AlcadaOficial alcadaOficial;

    public PosicaoDividaTools(DividaRepository repository,
                              CalculadoraAcordo calculadora,
                              AlcadaOficial alcadaOficial) {
        this.repository = repository;
        this.calculadora = calculadora;
        this.alcadaOficial = alcadaOficial;
    }

    @Tool(description = """
            Consulta a posicao atual de uma divida: carteira, dias em atraso, principal,
            encargos e saldo atualizado. Use sempre que precisar de qualquer valor do
            contrato. Nunca estime esses numeros por conta propria.
            """)
    public PosicaoDivida consultarPosicao(
            @ToolParam(description = "Numero do contrato, no formato CT-00000000") String contrato) {

        return repository.findByContrato(contrato)
                .orElseThrow(() -> new ContratoNaoEncontradoException(contrato));
    }

    @Tool(description = """
            Consulta o desconto maximo que a politica permite para um contrato, considerando
            a carteira, a faixa de atraso e a quantidade de parcelas. Use esta ferramenta
            ANTES de propor um desconto, para nao propor algo acima da alcada.
            """)
    public AlcadaDoContrato consultarAlcadaMaxima(
            @ToolParam(description = "Numero do contrato, no formato CT-00000000") String contrato,
            @ToolParam(description = "Quantidade de parcelas pretendida, minimo 1") int parcelas) {

        PosicaoDivida posicao = consultarPosicao(contrato);

        return new AlcadaDoContrato(
                posicao.contrato(),
                posicao.carteira(),
                posicao.diasEmAtraso(),
                FaixaAtraso.de(posicao.diasEmAtraso()),
                parcelas,
                alcadaOficial.maximoParcelado(posicao.carteira(), posicao.diasEmAtraso(), parcelas));
    }

    @Tool(description = """
            Simula um acordo para um contrato, aplicando um percentual de desconto sobre o
            saldo atualizado e dividindo em parcelas. Retorna os valores calculados, o teto
            da politica para esse caso, quem precisa aprovar e eventuais restricoes.
            Esta simulacao nao cria acordo nem altera nada: e apenas um calculo.
            Sempre relate ao analista o campo aprovacao e as restricoes, nunca so os valores.
            """)
    public SimulacaoAcordo simularAcordo(
            @ToolParam(description = "Numero do contrato, no formato CT-00000000") String contrato,
            @ToolParam(description = "Percentual de desconto sobre o saldo, de 0 a 100") BigDecimal percentualDesconto,
            @ToolParam(description = "Quantidade de parcelas, minimo 1") int parcelas) {

        PosicaoDivida posicao = consultarPosicao(contrato);
        return calculadora.simular(posicao, percentualDesconto, parcelas);
    }
}
