package br.tec.ebz.connid.connector.lg.model;

import java.util.List;

public record UserRecord(String cargo, String empresa, String organizacao, String matricula, String nome, String dataDeNascimento, String dataAdmissao, String dataDaRescisao, String dataDaInicioFerias, String dataFimFerias, String dataInicioAfastamento, String dataFimAfastamento, String codigoEmpresa, String gestorMatricula, String cpf, String email, List<String> historicoOcorrenciasFerias, List<String> historicoOcorrenciasAfastamento, Boolean isPrimary) {
    public UserRecord withPrimary(boolean primary) {
        return new UserRecord(
                cargo,
                empresa,
                organizacao,
                matricula,
                nome,
                dataDeNascimento,
                dataAdmissao,
                dataDaRescisao,
                dataDaInicioFerias,
                dataFimFerias,
                dataInicioAfastamento,
                dataFimAfastamento,
                codigoEmpresa,
                gestorMatricula,
                cpf,
                email,
                historicoOcorrenciasFerias,
                historicoOcorrenciasAfastamento,
                primary
        );
    }
}

