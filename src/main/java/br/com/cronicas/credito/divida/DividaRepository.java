package br.com.cronicas.credito.divida;

import br.com.cronicas.credito.dominio.Carteira;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Stand-in for the real ledger. In a production system this is the service that
 * already owns the balance; nothing about the AI layer changes it.
 */
@Repository
public class DividaRepository {

    private static final Map<String, PosicaoDivida> DADOS = Map.of(
            "CT-00188245", new PosicaoDivida("CT-00188245", Carteira.CONSIGNADO, 212,
                    new BigDecimal("4320.00"), new BigDecimal("1186.40"), new BigDecimal("5506.40")),
            "CT-00231907", new PosicaoDivida("CT-00231907", Carteira.CARTAO, 47,
                    new BigDecimal("1890.55"), new BigDecimal("212.33"), new BigDecimal("2102.88")),
            "CT-00245110", new PosicaoDivida("CT-00245110", Carteira.CDC_VEICULO, 95,
                    new BigDecimal("18750.00"), new BigDecimal("3112.50"), new BigDecimal("21862.50")));

    public Optional<PosicaoDivida> findByContrato(String contrato) {
        return Optional.ofNullable(DADOS.get(contrato));
    }
}
