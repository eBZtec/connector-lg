package br.tec.ebz.connid.connector.lg.model;

public record UserRecord(String cargo, String empresa, String organizacao, String matricula, String nome, String dataDeNascimento, String dataAdmissao, String dataDaRescisao, String codigoEmpresa, String gestorMatricula, String cpf, String email, Boolean isPrimary) {
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
                codigoEmpresa,
                gestorMatricula,
                cpf,
                email,
                primary
        );
    }
}

