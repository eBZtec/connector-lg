package br.tec.ebz.connid.connector.lg.utils;

public class TipoConsulta {

    public static final br.tec.ebz.connid.connector.lg.model.TipoConsulta RESCISAO_CONSULTA_LISTA = new br.tec.ebz.connid.connector.lg.model.TipoConsulta(
            "ferias",
            "ConsultarListaResponse",
            "ConsultarListaResult",
            null,
            "RescisaoParcial");

    public static final br.tec.ebz.connid.connector.lg.model.TipoConsulta FERIAS_CONSULTA_LISTA = new br.tec.ebz.connid.connector.lg.model.TipoConsulta(
            "ferias",
            "ConsultarListaResponse",
            "ConsultarListaResult",
            null,
            "FeriasParcial");

    public static final br.tec.ebz.connid.connector.lg.model.TipoConsulta AFASTAMENTO_CONSULTA_LISTA = new br.tec.ebz.connid.connector.lg.model.TipoConsulta(
            "ferias",
            "ConsultarListaResponse",
            "ConsultarListaResult",
            null,
            "AfastamentoParcial");

    public static final br.tec.ebz.connid.connector.lg.model.TipoConsulta FERIAS = new br.tec.ebz.connid.connector.lg.model.TipoConsulta(
            "ferias",
            "ConsultarListaDeModificadosPorFeriasResponse",
            "ConsultarListaDeModificadosPorFeriasResult",
            "ContratoDeTrabalhoModificadoPorFerias",
            "OperacaoDeFerias");

    public static final br.tec.ebz.connid.connector.lg.model.TipoConsulta AFASTAMENTO = new br.tec.ebz.connid.connector.lg.model.TipoConsulta(
            "afastamento",
            "ConsultarListaDeModificadosPorAfastamentoResponse",
            "ConsultarListaDeModificadosPorAfastamentoResult",
            "ContratoDeTrabalhoModificadoPorAfastamento",
            "OperacaoDeAfastamento");

    public static final br.tec.ebz.connid.connector.lg.model.TipoConsulta RESCISAO = new br.tec.ebz.connid.connector.lg.model.TipoConsulta(
            "demissao",
            "ConsultarListaDeModificadosPorRescisaoResponse",
            "ConsultarListaDeModificadosPorRescisaoResult",
            "ContratoDeTrabalhoModificadoPorRescisao",
            "OperacaoDeRescisao");
}
