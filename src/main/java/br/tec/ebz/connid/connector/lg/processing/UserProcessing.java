package br.tec.ebz.connid.connector.lg.processing;

import br.tec.ebz.connid.connector.lg.*;
import br.tec.ebz.connid.connector.lg.model.Gestor;
import br.tec.ebz.connid.connector.lg.model.PageResult;
import br.tec.ebz.connid.connector.lg.model.UserRecord;
import br.tec.ebz.connid.connector.lg.utils.ObjectHandler;
import br.tec.ebz.connid.connector.lg.utils.Body;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.objects.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static br.tec.ebz.connid.connector.lg.SchemaHandler.*;

public class UserProcessing extends ObjectHandler {
    private static final Log LOG = Log.getLog(UserProcessing.class);

    private final LGConnection connection;
    private final LGConfiguration configuration;

    public UserProcessing(LGConnection connection, LGConfiguration configuration) {
        this.connection = connection;
        this.configuration = configuration;
    }

    public void search(String uid, ResultsHandler resultsHandler) {
        int pageNumber = 1;
        PageResult page;
        do {
            page = fetchUserPage(pageNumber, uid);
            for (UserRecord user : page.users()) {
                if (uid == null) {
                    resultsHandler.handle(translate(user, null));
                } else {
                    int uidFiltro = Integer.parseInt(uid);
                    int uidUsuario = Integer.parseInt(user.matricula());

                    if (uidUsuario == uidFiltro) {
                        resultsHandler.handle(translate(user, null));
                        return;
                    }
                }
            }
            pageNumber++;
        } while (pageNumber <= page.totalPages());
    }

    private PageResult fetchUserPage(int page, String uid) {
        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try {
            String responseXml = executeSoapRequest(url,
                    Body.createUserManagerSearchBody("ConsultarContratosAplicandoAutorizacaoDePerfil",
                            String.format("<v1:PaginaAtual>%d</v1:PaginaAtual>",page)),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarContratosAplicandoAutorizacaoDePerfil", "Erro ao buscar usuário");

            XmlMapper mapper = new XmlMapper();

            JsonNode root = mapper.readTree(responseXml);
            JsonNode result = root.get("Body").get("ConsultarContratosAplicandoAutorizacaoDePerfilResponse").get("ConsultarContratosAplicandoAutorizacaoDePerfilResult");

            int totalPages = result.path("TotalDePaginas").asInt();

            JsonNode contratos = result.get("Retorno").path("ContratoDeTrabalhoV3");

            List<UserRecord> users = new ArrayList<>();

            for (JsonNode contrato : contratos) {
                String matricula = contrato.path("DadosGerais").path("Matricula").asString();

                if (uid != null && !uid.equals(matricula)) {
                    continue;
                }

                String empresa = contrato.path("Alocacao").path("Empresa").path("Codigo").asString();

                Map<String, String> gestor = findSuperior(matricula, empresa);

                users.add(new UserRecord(
                        contrato.path("Alocacao").path("CargoEFuncaoEfetivo").path("Cargo").path("Descricao").asString(),
                        contrato.path("Alocacao").path("Empresa").path("Descricao").asString(),
                        contrato.path("Alocacao").path("UnidadeOrganizacional").path("Descricao").asString(),
                        contrato.path("Pessoa").path("Contato").path("EmailCorporativo").asString(),
                        contrato.path("Pessoa").path("Contato").path("EmailParticular").asString(),
                        matricula,
                        contrato.path("Pessoa").path("DadosPessoais").path("Nome").asString(),
                        contrato.path("Pessoa").path("DadosPessoais").path("DataDeNascimento").asString(),
                        contrato.path("DadosGerais").path("DataAdmissao").asString(),
                        contrato.path("DadosDaRescisao").path("DataDaRescisao").asString(),
                        empresa,
                        gestor != null ? gestor.get(NOME_SUPERIOR) : null
                ));
            }
            return new PageResult(users, totalPages);
        } catch (Exception e) {
            LOG.error("Erro ao processar usuários: {0}", e.getMessage());
            throw new ConnectionFailedException(e.getMessage());
        }
    }

    private Map<String, String> findSuperior(String matricula, String empresa) {

        Map<String, String> superior = new HashMap<>();
        Gestor dadosSuperior = findManager(empresa, matricula);

        if (dadosSuperior == null || dadosSuperior.matricula() == null || dadosSuperior.matricula().isBlank()) {
            LOG.info("Superior não encontrado.");
            return null;
        }

        LOG.info(
                "Superior encontrado. Matrícula {0}, empresa {1}, situação {2} e nome {3}.",
                dadosSuperior.matricula(),
                dadosSuperior.empresa(),
                dadosSuperior.situacaoDoColaborador(),
                dadosSuperior.nome()
        );

        superior.put(SchemaHandler.NOME_SUPERIOR, dadosSuperior.nome());

        return superior;
    }

    private Gestor findManager(String codigoEmpresa, String matricula) {
        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try {
            String responseXml = executeSoapRequest(url,
                    Body.createUserManagerSearchBody("ConsultarListaGestoresNivelMaisProximo",
                            String.format("""
                                   <v11:Colaborador>
                                      <v11:Empresa>
                                        <v11:Codigo>%s</v11:Codigo>
                                      </v11:Empresa>
                                      <v11:Matricula>%s</v11:Matricula>
                                   </v11:Colaborador>
                                   """,
                                    codigoEmpresa,
                                    matricula)),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaGestoresNivelMaisProximo", "Erro ao buscar gestor");

            XmlMapper mapper = new XmlMapper();

            JsonNode root = mapper.readTree(responseXml);

            JsonNode result = root.path("Body")
                    .path("ConsultarListaGestoresNivelMaisProximoResponse")
                    .path("ConsultarListaGestoresNivelMaisProximoResult");

            JsonNode contrato = result.path("Retorno").path("ContratoDeTrabalhoParcial");

            return new Gestor(
                    contrato.path("Matricula").asString(),
                    contrato.path("Pessoa").path("Nome").asString(),
                    contrato.path("SituacaoDoColaborador").path("Descricao").asString(),
                    contrato.path("Empresa").path("Codigo").asString()
            );

        } catch (Exception e) {
            LOG.error("Erro ao processar gestor: {0}", e.getMessage());
            throw new ConnectionFailedException(e.getMessage());
        }
    }

    private String executeSoapRequest(String url, String body, String soapAction, String mensagemErro) throws IOException {
        HttpPost post = new HttpPost(url);
        String xml = connection.headler(body);

        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", soapAction);

        return connection.postRequest(post, mensagemErro);
    }

    public ConnectorObject findByMatricula(String matricula, String dataDemissao) throws IOException {
        LOG.info("Buscando matrícula {0}", matricula);
        int pageNumber = 1;
        PageResult page;

        do {
            page = fetchUserPage(pageNumber, matricula);

            for (UserRecord user : page.users()) {
                LOG.info("Matrícula encontrada na página: {0}", user.matricula());
                if (matricula.equals(user.matricula())) {
                    LOG.info("Usuário localizado.");
                    return translate(user, dataDemissao);
                }
            }
            pageNumber++;
        } while (pageNumber <= page.totalPages());
        return null;
    }

    public void sync(SyncResultsHandler syncResultsHandler) throws IOException {

        LOG.info("Inicio do processo de sincronização:");

        String endPoint = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        Date currentDate = new Date();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        calendar.add(Calendar.DATE, -29);
        Date lastDate = calendar.getTime();

        Calendar startDateCalendar = Calendar.getInstance();
        startDateCalendar.setTime(currentDate);
        startDateCalendar.add(Calendar.DATE, 1);
        Date currentDatePlus1 = startDateCalendar.getTime();

//        String endDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDatePlus1);
//        String startDate = new SimpleDateFormat("yyyy-MM-dd").format(lastDate);

        String endDate = "2026-01-30";
        String startDate = "2026-01-01";

        String[] companies = configuration.getEmpresas().split(",");

        for (String company : companies) {
            LOG.info("Pesquisando demissões da empresa {0}", company);
            LOG.info("Período consultado: {0} até {1}", startDate, endDate);

            String responseDemitidoXml = executeSoapRequest(endPoint,
                    Body.createModifiedContractsSearchBody ("ConsultarListaDeModificadosPorRescisao", company, endDate, startDate,
                            "",""),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorRescisao",
                    "Erro ao buscar demissão");

            String responseFeriasXml = executeSoapRequest(endPoint,
                    Body.createModifiedContractsSearchBody ("ConsultarListaDeModificadosPorFerias", company, endDate, startDate,
                            "<v1:Valor>3</v1:Valor>", "<v1:TipoDeFerias>1064</v1:TipoDeFerias>"),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorAfastamento",
                    "Erro ao buscar férias");;

            String responseAfastamentoXml = executeSoapRequest(endPoint,
                    Body.createModifiedContractsSearchBody ("ConsultarListaDeModificadosPorAfastamento", company, endDate, startDate,
                            "",""),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorAfastamento",
                    "Erro ao buscar afastamento");

            LOG.info("Resposta SOAP: {0}", responseDemitidoXml);

            XmlMapper mapper = new XmlMapper();
            JsonNode root = mapper.readTree(responseDemitidoXml);

            JsonNode contratos = root.path("Body")
                    .path("ConsultarListaDeModificadosPorRescisaoResponse")
                    .path("ConsultarListaDeModificadosPorRescisaoResult")
                    .path("Retorno")
                    .path("ContratoDeTrabalhoModificadoPorRescisao");

            if (root.isMissingNode()
                    || root.isNull()
                    || contratos.isMissingNode()
                    || contratos.isNull()) {

                LOG.info("Empresa {0} sem usuários rescindidos no período {1} até {2}.", company, startDate,endDate);

                continue;
            }

            if (!contratos.isArray()) {
                contratos = mapper.createArrayNode().add(contratos);
            }

            LOG.info("Empresa {0}: encontrados {1} contrato(s) rescindido(s).",company,contratos.size());

            for (JsonNode objetoDemissao : contratos) {

                JsonNode operacoes = objetoDemissao.path("Operacoes").path("OperacaoDeRescisao");

                if (!operacoes.isArray()) {
                    operacoes = mapper.createArrayNode().add(operacoes);
                }

                for (JsonNode operacao : operacoes) {

                    String matricula = objetoDemissao.path("IdentificacaoDeContrato").path("Matricula").asString();
                    String dataRescisao = operacao.path("DataDaRescisao").asString();

                    LOG.info("Processando matrícula {0}", matricula);

                    ConnectorObject usuarioDemitido = findByMatricula(matricula, dataRescisao);

                    LOG.info("Resultado da busca: {0}", usuarioDemitido);

                    if (usuarioDemitido != null) {

                        SyncDeltaBuilder builder = new SyncDeltaBuilder();

                        builder.setObjectClass(ObjectClass.ACCOUNT);
                        builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                        builder.setObject(usuarioDemitido);
                        builder.setToken(new SyncToken(System.currentTimeMillis()));

                        syncResultsHandler.handle(builder.build());
                    }
                }
            }
            LOG.info("Fim do processo da empresa {0}", company);
        }
    }

    private ConnectorObject translate(UserRecord user, String dataDemissao) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.ACCOUNT);

        addAttribute(builder, Name.NAME, user.matricula());
        addAttribute(builder, Uid.NAME, user.matricula());
        addAttribute(builder, USER_NOME, user.nome());
        addAttribute(builder, USER_CARGO, user.cargo());
        addAttribute(builder, USER_EMPRESA, user.empresa());
        addAttribute(builder, USER_OU, user.organizacao());
        addAttribute(builder, USER_DATA_ADMISSAO, user.dataAdmissao());
        addAttribute(builder, USER_EMAIL_CORPORATIVO, user.emailCorporativo());
        addAttribute(builder, USER_EMAIL_PATICULAR, user.emailParticular());
        addAttribute(builder, USER_DATA_DE_NASCIMENTO, user.dataDeNascimento());
        addAttribute(builder, USER_DATA_DE_RESCISAO, user.dataDaRescisao());
        addAttribute(builder, NOME_SUPERIOR, user.gestor());
        if (dataDemissao != null && !dataDemissao.isBlank()) {
            addAttribute(builder, "dataDaRescisao", dataDemissao);
        }

        return builder.build();
    }


    public ObjectClassInfoBuilder userSchemaBuilder() {
        ObjectClassInfoBuilder objectClassInfoBuilder = new ObjectClassInfoBuilder();
        objectClassInfoBuilder.setType(ObjectClass.ACCOUNT_NAME);

        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        Uid.NAME,
                        String.class,
                        USER_MATRICULA,
                        AttributeInfo.Flags.REQUIRED
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        Uid.NAME,
                        String.class,
                        USER_NOME,
                        AttributeInfo.Flags.REQUIRED
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_NOME,
                        String.class,
                        USER_NOME,
                        AttributeInfo.Flags.REQUIRED
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_CARGO,
                        String.class,
                        USER_CARGO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_EMPRESA,
                        String.class,
                        USER_EMPRESA
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_OU,
                        String.class,
                        USER_OU
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_ADMISSAO,
                        Date.class,
                        USER_DATA_ADMISSAO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_EMAIL_CORPORATIVO,
                        String.class,
                        USER_EMAIL_CORPORATIVO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_EMAIL_PATICULAR,
                        String.class,
                        USER_EMAIL_PATICULAR
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_DE_NASCIMENTO,
                        Date.class,
                        USER_DATA_DE_NASCIMENTO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_DE_RESCISAO,
                        Date.class,
                        USER_DATA_DE_RESCISAO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        NOME_SUPERIOR,
                        Date.class,
                        NOME_SUPERIOR
                )
        );
        return objectClassInfoBuilder;
    }
}
