package br.tec.ebz.connid.connector.lg.model;

import java.util.Date;

public record UserRecord(String cargo, String empresa, String organizacao, String emailCorporativo, String emailParticular, String matricula, String nome, Date dataDeNascimento, Date dataAdmissao, Date dataDaRescisao) {
}

