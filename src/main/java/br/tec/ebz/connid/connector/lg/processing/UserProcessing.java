package br.tec.ebz.connid.connector.lg.processing;

import br.tec.ebz.connid.connector.lg.*;
import br.tec.ebz.connid.connector.lg.model.Gestor;
import br.tec.ebz.connid.connector.lg.model.PageResult;
import br.tec.ebz.connid.connector.lg.model.UserRecord;
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

public class UserProcessing extends ObjectProcessing {
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
            String responseXml = fetchContractsPage(url, page);

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

                Map<String, String> gestor = findActiveSuperior(matricula, empresa);

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

    private String fetchContractsPage(String url, int paginaAtual) throws IOException {
        HttpPost post = new HttpPost(url);

        String body = createUserManagerSearchBody("ConsultarContratosAplicandoAutorizacaoDePerfil",
                String.format("<v1:PaginaAtual>%d</v1:PaginaAtual>",paginaAtual));

        String xml = connection.headler(body);
        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarContratosAplicandoAutorizacaoDePerfil");

        return connection.postRequest(post,"Erro ao buscar usuário");
    }

    private Map<String, String> findActiveSuperior(String matricula, String empresa) {

        Map<String, String> superior = new HashMap<>();

        Gestor dadosSuperior = findManager(empresa, matricula);

        if (dadosSuperior == null || dadosSuperior.matricula() == null || dadosSuperior.matricula().isBlank()) {
            LOG.info("Superior não encontrado.");
            return null;
        }

        String matriculaSuperior = dadosSuperior.matricula();
        String situacaoSuperior = dadosSuperior.situacaoDoColaborador();
        String empresaSuperior = dadosSuperior.empresa();

        LOG.info("Encontrado o superior. Matricula {0} empresa {1} e situação {2}. Validando situação.", matriculaSuperior, empresaSuperior, situacaoSuperior);

        String situacaoTrabalhoNormal = "ATIVIDADE NORMAL";

        while (!dadosSuperior.situacaoDoColaborador().contains(situacaoTrabalhoNormal)) {
            LOG.info("Superior {0} está com a situação diferente de 'ATIVIDADE NORMAL'. Situação {1}, procurando pelo superior dele.", matriculaSuperior, situacaoSuperior);

            dadosSuperior = findManager(empresaSuperior, matriculaSuperior);

            if (dadosSuperior == null || dadosSuperior.matricula() == null || dadosSuperior.matricula().isBlank()) {
                LOG.info("Não foi encontrado outro superior.");
                return null;
            }

            LOG.info("Superior encontrado: {0}", dadosSuperior);

            matriculaSuperior = dadosSuperior.matricula();
            situacaoSuperior = dadosSuperior.situacaoDoColaborador();
        }

        String nomeSuperior = dadosSuperior.nome();

        LOG.info("Superior validado com sucesso. Matricula {0}, empresa {1}, situação {2} e nome {3}.", matriculaSuperior, empresaSuperior, situacaoSuperior, nomeSuperior);

        superior.put(SchemaHandler.NOME_SUPERIOR, nomeSuperior);

        return superior;
    }

    private Gestor findManager(String codigoEmpresa, String matricula) {
        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try {
            String responseXml = executeManagerRequest(url, codigoEmpresa, matricula);

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

    private String executeManagerRequest(String url, String codigoEmpresa, String matricula) throws IOException {
        HttpPost post = new HttpPost(url);
        String body = createUserManagerSearchBody("ConsultarListaGestoresNivelMaisProximo",
                String.format("""
                                   <v11:Colaborador>
                                      <v11:Empresa>
                                        <v11:Codigo>%s</v11:Codigo>
                                      </v11:Empresa>
                                      <v11:Matricula>%s</v11:Matricula>
                                   </v11:Colaborador>
                                   """,
                        codigoEmpresa,
                        matricula));
        String xml = connection.headler(body);

        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaGestoresNivelMaisProximo");

        return connection.postRequest(post,"Erro ao buscar gestor");
    }

    private String fetchVacationChanges(String url, String codigoEmpresa, String dataFim, String dataInicio) throws IOException {
        HttpPost post = new HttpPost(url);
        String body = createModifiedContractsSearchBody ("ConsultarListaDeModificadosPorFerias", codigoEmpresa, dataFim, dataInicio,
                "<v1:Valor>3</v1:Valor>", "<v1:TipoDeFerias>1064</v1:TipoDeFerias>");
        String xml = connection.headler(body);

        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorFerias");

        return connection.postRequest(post,"Erro ao buscar férias");
    }

    private String fetchTerminationChanges(String url, String codigoEmpresa, String dataFim, String dataInicio) throws IOException {
        HttpPost post = new HttpPost(url);
        String body = createModifiedContractsSearchBody ("ConsultarListaDeModificadosPorRescisao", codigoEmpresa, dataFim, dataInicio,
                "","");
        String xml = connection.headler(body);

        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorRescisao");

        return connection.postRequest(post,"Erro ao buscar demissão");
    }

    private String fetchLeaveChanges(String url, String codigoEmpresa, String dataFim, String dataInicio) throws IOException {
        HttpPost post = new HttpPost(url);
        String body = createModifiedContractsSearchBody ("ConsultarListaDeModificadosPorAfastamento", codigoEmpresa, dataFim, dataInicio,"","");
        String xml = connection.headler(body);

        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorAfastamento");

        return connection.postRequest(post,"Erro ao buscar afastamento");
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

        LOG.info("Inicio do processo de sincronização para demissão:");

        String endPointDemitido = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

//        String endPointFerias = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

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

            String responseXml = fetchTerminationChanges(endPointDemitido, company, endDate, startDate);

            LOG.info("Resposta SOAP: {0}", responseXml);

            XmlMapper mapper = new XmlMapper();
            JsonNode root = mapper.readTree(responseXml);

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
                    String empresa = objetoDemissao.path("IdentificacaoDeContrato").path("Empresa").path("Codigo").asString();
                    String tipoOperacao = operacao.path("TipoDeOperacao").asString();
                    String dataRescisao = operacao.path("DataDaRescisao").asString();

                    LOG.info(
                            "Matrícula {0}, Empresa {1}, Tipo {2}, Data {3}",
                            matricula,
                            empresa,
                            tipoOperacao,
                            dataRescisao
                    );

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

    public String createUserManagerSearchBody(String tipo, String filtro){
        return String.format("""
                        <soapenv:Body>
                                <v2:%1$s>
                                 <v2:filtro>
                                    %2$s
                                   <v11:TiposDeSituacoes>
                                       <arr:int>1</arr:int> <!--1 - Afastamento-->
                        			   <arr:int>2</arr:int> <!--2 - Atividade Normal-->
                        			   <arr:int>3</arr:int> <!--3 - Férias-->
                        			   <arr:int>4</arr:int> <!--4 - Recesso-->
                        			   <arr:int>5</arr:int> <!--5 - Rescisão-->
                                    </v11:TiposDeSituacoes>
                                 </v2:filtro>
                              </v2:%1$s>
                           </soapenv:Body>
                        """,
                tipo,
                filtro
        );
    }

    public String createModifiedContractsSearchBody(String tipo, String codigoEmpresa,String dataFim, String dataInicio, String operacaoExtra,String complemento) {
        return String.format("""
            <soapenv:Body>
               <v2:%1$s>
                  <v2:filtro>
                     <v1:CodigoDaEmpresa>%2$s</v1:CodigoDaEmpresa>
                     <v1:PeriodoDeBusca>
                        <v1:DataFim>%3$s</v1:DataFim>
                        <v1:DataInicio>%4$s</v1:DataInicio>
                     </v1:PeriodoDeBusca>
                     <v1:TiposDeOperacoes>
                        <v1:TipoDeOperacaoDaOcorrencia>
                           <v1:Valor>1</v1:Valor>
                           <v1:Valor>2</v1:Valor>
                           %5$s
                        </v1:TipoDeOperacaoDaOcorrencia>
                     </v1:TiposDeOperacoes>
                     %6$s
                  </v2:filtro>
               </v2:%1$s>
            </soapenv:Body>
            """,
                tipo,
                codigoEmpresa,
                dataFim,
                dataInicio,
                operacaoExtra,
                complemento
        );
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
