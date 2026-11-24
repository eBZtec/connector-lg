package br.tec.ebz.connid.connector.lg.model;

import java.util.List;

public record ResultadoOcorrencias(Periodo ultimoPeriodo, List<String> historico) {}