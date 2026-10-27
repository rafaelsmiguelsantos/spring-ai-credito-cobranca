package br.com.cronicas.credito.divida;

import br.com.cronicas.credito.dominio.Carteira;
import java.math.BigDecimal;

public record PosicaoDivida(
        String contrato,
        Carteira carteira,
        int diasEmAtraso,
        BigDecimal principal,
        BigDecimal encargos,
        BigDecimal saldoAtualizado) {
}
