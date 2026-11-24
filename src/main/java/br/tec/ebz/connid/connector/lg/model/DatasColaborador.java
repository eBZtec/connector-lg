package br.tec.ebz.connid.connector.lg.model;

import java.util.List;

public record DatasColaborador(
        String dataRescisao,
        String inicioFerias,
        String fimFerias,
        String inicioAfastamento,
        String fimAfastamento,
        List<String> historicoFerias,
        List<String> historicoAfastamento
) {
}