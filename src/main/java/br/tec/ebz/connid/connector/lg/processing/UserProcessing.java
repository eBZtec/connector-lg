package br.tec.ebz.connid.connector.lg.processing;

import br.tec.ebz.connid.connector.lg.*;
import br.tec.ebz.connid.connector.lg.model.PageResult;
import br.tec.ebz.connid.connector.lg.model.TipoConsulta;
import br.tec.ebz.connid.connector.lg.model.UserRecord;
import br.tec.ebz.connid.connector.lg.utils.ObjectHandler;
import br.tec.ebz.connid.connector.lg.utils.Body;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        if (uid == null) {
            int pageNumber = 1;
            PageResult page;
            Map<String, List<UserRecord>> allUsersByCPF = new HashMap<>();
            do {
                page = fetchUserPage(pageNumber);
                for (UserRecord user : page.users()) {
                    allUsersByCPF.computeIfAbsent(user.cpf(), k -> new ArrayList<>())
                            .add(user);
                }
                //if (pageNumber == 5) break;
                pageNumber++;
            } while (pageNumber <= page.totalPages());
            //LOG.info("Usuários por CPF: {0}", allUsersByCPF);
            for (Map.Entry<String, List<UserRecord>> entry : allUsersByCPF.entrySet()) {
                String cpf = entry.getKey();
                List<UserRecord> registrosPorMatricula = entry.getValue();
                LOG.info("======= Inicio Validacao =======");
                LOG.info("Validando CPF: {0}. Quantidade de contas encontradas: {1}", cpf, registrosPorMatricula.size());

                Optional<UserRecord> ativo = registrosPorMatricula.stream()
                        .filter(u -> u.dataDaRescisao() == null || u.dataDaRescisao().isBlank())
                        .findFirst();

                if (ativo.isPresent()) {

                    UserRecord primario = ativo.get();

                    for (int i = 0; i < registrosPorMatricula.size(); i++) {
                        UserRecord usuario = registrosPorMatricula.get(i);
                        registrosPorMatricula.set(i, usuario.withPrimary(usuario.equals(primario)));
                    }

                } else {

                    UserRecord maisRecente = registrosPorMatricula.stream()
                            .max(Comparator.comparing(u -> LocalDateTime.parse(u.dataDaRescisao())))
                            .orElse(null);

                    if (maisRecente != null) {
                        for (int i = 0; i < registrosPorMatricula.size(); i++) {
                            UserRecord usuario = registrosPorMatricula.get(i);
                            registrosPorMatricula.set(i, usuario.withPrimary(usuario.equals(maisRecente)));
                        }
                    }
                }
                LOG.info("registros após validacao: {0}", registrosPorMatricula);
                for (UserRecord userToMidpoint : registrosPorMatricula) {
                    boolean buildOk = resultsHandler.handle(translate(userToMidpoint, null, null, null));
                    if(buildOk){
                        LOG.info("Deu certo");
                    }else{
                        LOG.error("não deu certo");
                    }
                }
                LOG.info("======= Fim Validacao =======");
            }
        } else {
            UserRecord user = fetchUserByUid(uid,null);
            if (user != null) {
                boolean buildOk = resultsHandler.handle(translate(user, null, null, null));
                if(buildOk){
                    LOG.info("Deu certo");
                }else{
                    LOG.error("não deu certo");
                }
            }
        }
    }

    private UserRecord fetchUserByUid(String uid, String dataDaRescisao){
        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        String[] uidPartes = uid.split(";");
        String codigoEmpresa = uidPartes[1];
        String id = uidPartes[0];

        try {
            String responseXml = executeSoapRequest(url,
                    Body.createUserFilter("Consultar", codigoEmpresa, id),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/Consultar", "Erro ao buscar usuário");

            XmlMapper mapper = new XmlMapper();

            JsonNode root = mapper.readTree(responseXml);
            JsonNode contrato = root.get("Body").get("ConsultarResponse").get("ConsultarResult").get("Retorno");

            if (contrato == null) {
                LOG.info("Nenhum contrato encontrado para {0}", uid);
                return null;
            }

            String matricula = contrato.path("Matricula").asString();
            String cpf = contrato.path("Pessoa").path("Cpf").asString();
            Boolean isPrimary = defineIfIsPrimary(matricula, cpf);

            return new UserRecord(
                    contrato.path("Cargo").path("Descricao").asString(),
                    contrato.path("Empresa").path("Descricao").asString(),
                    contrato.path("UnidadeOrganizacional").path("Descricao").asString(),
                    matricula,
                    contrato.path("Pessoa").path("Nome").asString(),
                    contrato.path("Pessoa").path("DataDeNascimento").asString(),
                    contrato.path("DataAdmissao").asString(),
                    dataDaRescisao,
                    contrato.path("Empresa").path("Codigo").asString(),
                    null,
                    cpf,
                    null,
                    isPrimary
            );
        } catch (Exception e) {
            LOG.error("Erro ao processar usuários: {0}", e.getMessage());
            throw new ConnectionFailedException(e.getMessage());
        }
    }

    private Boolean defineIfIsPrimary(String matricula, String cpf) {
        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try {
            String responseXml = executeSoapRequest(
                    url,
                    Body.createUserSearchBody(
                            "ConsultarContratosAplicandoAutorizacaoDePerfil",
                            String.format("""
                                    <v1:FiltrosEspecificos>
                                        <v1:FiltroDeCamposEspecificos>
                                            <v1:Campo>3</v1:Campo>
                                            <v1:Operacao>0</v1:Operacao>
                                            <v1:ValoresParaFiltrar>
                                                <arr:string>%s</arr:string>
                                            </v1:ValoresParaFiltrar>
                                        </v1:FiltroDeCamposEspecificos>
                                    </v1:FiltrosEspecificos>
                                    <v1:PaginaAtual>1</v1:PaginaAtual>
                                    """, cpf)),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarContratosAplicandoAutorizacaoDePerfil",
                    "Erro ao buscar usuários para definir se primária");

            XmlMapper mapper = new XmlMapper();

            JsonNode root = mapper.readTree(responseXml);

            JsonNode contratos = root.path("Body").path("ConsultarContratosAplicandoAutorizacaoDePerfilResponse")
                    .path("ConsultarContratosAplicandoAutorizacaoDePerfilResult").path("Retorno").path("ContratoDeTrabalhoV3");

            LocalDate dataMaisRecente = null;
            String matriculaPrimaria = null;
            for (JsonNode contrato : contratos) {
                LOG.info("contrato encontrado: {0}", contrato);
                String matriculaAtual = contrato.path("DadosGerais").path("Matricula").asString();

                if (contrato.has("DadosDaRescisao")) {
                    String dataRescisaoStr = contrato.path("DadosDaRescisao").path("DataDaRescisao").asString();

                    LocalDate dataRescisao = LocalDateTime
                            .parse(dataRescisaoStr)
                            .toLocalDate();

                    if (dataMaisRecente == null || dataRescisao.isAfter(dataMaisRecente)) {
                        dataMaisRecente = dataRescisao;
                        matriculaPrimaria = matriculaAtual;
                    }

                } else {
                    LOG.info("data de rescisão presente? {0}", contrato.has("DadosDaRescisao"));
                    if (matricula.equals(matriculaAtual)) {
                        return true;
                    } else {
                        LOG.info("conta não é primária");
                        return false;
                    }
                }
            }

            return (matricula.equals(matriculaPrimaria));

        } catch (Exception e) {
            throw new ConnectorException("//defineIfIsPrimary - Erro ao definir se a conta é primária " + e.getMessage());
        }
    }

    private PageResult fetchUserPage(int page) {
        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try {
            String responseXml = executeSoapRequest(url,
                    Body.createUserSearchBody("ConsultarContratosAplicandoAutorizacaoDePerfil",
                            String.format("<v1:PaginaAtual>%d</v1:PaginaAtual>", page)),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarContratosAplicandoAutorizacaoDePerfil", "Erro ao buscar usuários");

            XmlMapper mapper = new XmlMapper();

            JsonNode root = mapper.readTree(responseXml);
            JsonNode result = root.get("Body").get("ConsultarContratosAplicandoAutorizacaoDePerfilResponse").get("ConsultarContratosAplicandoAutorizacaoDePerfilResult");

            int totalPages = result.path("TotalDePaginas").asInt();

            JsonNode contratos = result.get("Retorno").path("ContratoDeTrabalhoV3");

            List<UserRecord> users = new ArrayList<>();

            for (JsonNode contrato : contratos) {
                String matricula = contrato.path("DadosGerais").path("Matricula").asString();
                String cpf = contrato.path("Pessoa").path("DadosPessoais").path("CpfCnpj").asString();
                users.add(new UserRecord(
                        contrato.path("Alocacao").path("CargoEFuncaoEfetivo").path("Cargo").path("Descricao").asString(),
                        contrato.path("Alocacao").path("Empresa").path("Descricao").asString(),
                        contrato.path("Alocacao").path("UnidadeOrganizacional").path("Descricao").asString(),
                        matricula,
                        contrato.path("Pessoa").path("DadosPessoais").path("Nome").asString(),
                        contrato.path("Pessoa").path("DadosPessoais").path("DataDeNascimento").asString(),
                        contrato.path("DadosGerais").path("DataAdmissao").asString(),
                        contrato.path("DadosDaRescisao").path("DataDaRescisao").asString(),
                        contrato.path("Alocacao").path("Empresa").path("Codigo").asString(),
                        contrato.path("Alocacao").path("ListaDeGestores").path("IdentificacaoDeGestorV2").path("Matricula").asString(),
                        cpf,
                        contrato.path("Pessoa").path("Contato").path("EmailCorporativo").asString(),
                        null
                ));
            }
            return new PageResult(users, totalPages);
        } catch (Exception e) {
            LOG.error("Erro ao processar usuários: {0}", e.getMessage());
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

    public ConnectorObject findByMatricula(String matricula, TipoConsulta consulta, String dataInicio, String dataFim) {
        LOG.info("Buscando matrícula {0}", matricula);

        UserRecord user = fetchUserByUid(matricula, null);

        if (user != null) {
            LOG.info("Usuário localizado: {0}", user.matricula());
            return translate(user, consulta, dataInicio, dataFim);
        }

        return null;
    }

    public ConnectorObject findByMatriculaRecissao(String matricula, TipoConsulta consulta, String dataRecissao) {
        LOG.info("Buscando matrícula {0}", matricula);

        UserRecord user = fetchUserByUid(matricula, dataRecissao);

        if (user != null) {
            LOG.info("Usuário localizado: {0}", user.matricula());
            return translate(user, consulta, null, null);
        }

        return null;
    }

    public void sync(SyncResultsHandler syncResultsHandler) throws IOException {

        LOG.info("Inicio do processo de sincronização:");

        String endPoint = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        Date currentDate = new Date();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        calendar.add(Calendar.DATE, -1);
        Date lastDate = calendar.getTime();

        Calendar startDateCalendar = Calendar.getInstance();
        startDateCalendar.setTime(currentDate);
        startDateCalendar.add(Calendar.DATE, 1);
        Date currentDatePlus1 = startDateCalendar.getTime();

        String endDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDatePlus1);
        String startDate = new SimpleDateFormat("yyyy-MM-dd").format(lastDate);

        String[] companies = configuration.getEmpresas().split(",");

        for (String company : companies) {
            LOG.info("Pesquisando ações da empresa {0}", company);
            LOG.info("Período consultado: {0} até {1}", startDate, endDate);

            String responseFeriasXml = executeSoapRequest(endPoint,
                    Body.createModifiedContractsSearchBody("ConsultarListaDeModificadosPorFerias", company, endDate, startDate,
                            """
                                    <v1:TiposDeOperacoes>
                                       <v1:TipoDeOperacaoDaOcorrencia>
                                          <v1:Valor>1</v1:Valor>
                                       </v1:TipoDeOperacaoDaOcorrencia>
                                    </v1:TiposDeOperacoes>
                                    <v1:TipoDeFerias>1064</v1:TipoDeFerias>
                                    """),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorFerias",
                    "Erro ao buscar férias");

            String responseAfastamentoXml = executeSoapRequest(endPoint,
                    Body.createModifiedContractsSearchBody("ConsultarListaDeModificadosPorAfastamento", company, endDate, startDate,
                            """
                                    <v1:TiposDeOperacoes>
                                       <v1:TipoDeOperacaoDaOcorrencia>
                                          <v1:Valor>1</v1:Valor>
                                       </v1:TipoDeOperacaoDaOcorrencia>
                                       <v1:TipoDeOperacaoDaOcorrencia>
                                          <v1:Valor>2</v1:Valor>
                                       </v1:TipoDeOperacaoDaOcorrencia>
                                    </v1:TiposDeOperacoes>
                                    """),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorAfastamento",
                    "Erro ao buscar afastamento");

            String responseDemissaoXml = executeSoapRequest(endPoint,
                    Body.createModifiedContractsSearchBody("ConsultarListaDeModificadosPorRescisao", company, endDate, startDate,
                            """
                                    <v1:TiposDeOperacoes>
                                       <v1:TipoDeOperacaoDaOcorrencia>
                                          <v1:Valor>1</v1:Valor>
                                       </v1:TipoDeOperacaoDaOcorrencia>
                                       <v1:TipoDeOperacaoDaOcorrencia>
                                          <v1:Valor>2</v1:Valor>
                                       </v1:TipoDeOperacaoDaOcorrencia>
                                    </v1:TiposDeOperacoes>
                                    """),
                    "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarListaDeModificadosPorRescisao",
                    "Erro ao buscar Demitidos");

            LOG.info("Resposta SOAP: {0}", responseFeriasXml);
            LOG.info("Resposta SOAP: {0}", responseAfastamentoXml);
            LOG.info("Resposta SOAP: {0}", responseDemissaoXml);

            XmlMapper mapper = new XmlMapper();

            processarFeriasAfastamentos(responseFeriasXml, company, FERIAS, startDate, endDate, mapper, syncResultsHandler);

            processarFeriasAfastamentos(responseAfastamentoXml, company, AFASTAMENTO, startDate, endDate, mapper, syncResultsHandler);

            processarDemissao(responseDemissaoXml, company, DEMISSAO, startDate, endDate, mapper, syncResultsHandler);

            LOG.info("Fim do processo da empresa {0}", company);
        }
    }

    public void processarDemissao(String responseXml, String company, TipoConsulta consulta, String startDate, String endDate, XmlMapper mapper, SyncResultsHandler handler) {
        JsonNode root = mapper.readTree(responseXml);

        JsonNode contratos = root.path("Body")
                .path(consulta.responseNode())
                .path(consulta.resultNode())
                .path("Retorno")
                .path(consulta.contratoNode());

        if (contratos.isMissingNode() || contratos.isNull()) {
            LOG.info("Empresa {0} sem usuários de {1} no período {2} até {3}.",
                    company, consulta.tipo(), startDate, endDate);
            return;
        }

        if (!contratos.isArray()) {
            contratos = mapper.createArrayNode().add(contratos);
        }

        for (JsonNode objeto : contratos) {

            JsonNode operacoes = objeto.path("Operacoes")
                    .path(consulta.operacaoNode());

            if (operacoes.isMissingNode() || operacoes.isNull()) {
                continue;
            }

            if (!operacoes.isArray()) {
                operacoes = mapper.createArrayNode().add(operacoes);
            }

            String matricula = objeto.path("IdentificacaoDeContrato")
                    .path("Matricula")
                    .asString();

            for (JsonNode operacao : operacoes) {

                String dataDaRescisao = operacao
                        .path("DataDaRescisao")
                        .asString();

                String tipoOperacao = operacao
                        .path("TipoDeOperacao")
                        .asString();

                String rescisaoAtiva = operacao
                        .path("RescisaoAtiva")
                        .asString();

                LOG.info(
                        "Processando {0} - Matrícula {1}, TipoOperacao {2}, DataDaRescisao {3}, RescisaoAtiva {4}",
                        consulta.tipo(),
                        matricula,
                        tipoOperacao,
                        dataDaRescisao,
                        rescisaoAtiva
                );

                String uid = matricula + ";" + company;

                ConnectorObject usuario = findByMatriculaRecissao(
                        uid,
                        consulta,
                        dataDaRescisao
                );

                if (usuario != null) {

                    SyncDeltaBuilder builder = new SyncDeltaBuilder();

                    builder.setObjectClass(ObjectClass.ACCOUNT);
                    builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                    builder.setObject(usuario);
                    builder.setToken(new SyncToken(System.currentTimeMillis()));

                    SyncDelta delta = builder.build();

                    boolean buildOk = handler.handle(delta);

                    if(buildOk){
                        LOG.info("Deu certo");
                    }else{
                        LOG.error("não deu certo");
                    }
                }
            }
        }
    }

    public void processarFeriasAfastamentos(String responseXml, String company, TipoConsulta consulta, String startDate, String endDate, XmlMapper mapper, SyncResultsHandler handler) {
        JsonNode root = mapper.readTree(responseXml);

        JsonNode contratos = root.path("Body")
                .path(consulta.responseNode())
                .path(consulta.resultNode())
                .path("Retorno")
                .path(consulta.contratoNode());

        if (contratos.isMissingNode() || contratos.isNull()) {
            LOG.info("Empresa {0} sem usuários de {1} no período {2} até {3}.",
                    company, consulta.tipo(), startDate, endDate);
            return;
        }

        if (!contratos.isArray()) {
            contratos = mapper.createArrayNode().add(contratos);
        }

        for (JsonNode objeto : contratos) {

            JsonNode operacoes = objeto.path("Operacoes")
                    .path(consulta.operacaoNode());

            if (!operacoes.isArray()) {
                operacoes = mapper.createArrayNode().add(operacoes);
            }

            JsonNode periodoSelecionado = null;
            LocalDateTime dataHoraMaisRecente = null;

            for (JsonNode operacao : operacoes) {

                String dataHoraStr = operacao.path("DataEHora").asString();

                if (dataHoraStr == null || dataHoraStr.isBlank()) {
                    continue;
                }

                LocalDateTime dataHora = LocalDateTime.parse(dataHoraStr);

                if (dataHoraMaisRecente == null || dataHora.isAfter(dataHoraMaisRecente)) {
                    dataHoraMaisRecente = dataHora;
                    periodoSelecionado = operacao;
                }
            }

            if (periodoSelecionado == null) {
                continue;
            }

            JsonNode periodo = periodoSelecionado.path("PeriodoDaOcorrencia");

            String matricula = objeto.path("IdentificacaoDeContrato")
                    .path("Matricula")
                    .asString();

            String inicio = periodo.path("DataInicio").asString();
            String fim = periodo.path("DataFim").asString();

            LOG.info("Processando {0} - Matrícula {1}, início {2}, fim {3}",
                    consulta.tipo(), matricula, inicio, fim);

            String uid = matricula + ";" + company;

            ConnectorObject usuario = findByMatricula(uid, consulta, inicio, fim);

            if (usuario != null) {

                SyncDeltaBuilder builder = new SyncDeltaBuilder();
                builder.setObjectClass(ObjectClass.ACCOUNT);
                builder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                builder.setObject(usuario);
                builder.setToken(new SyncToken(System.currentTimeMillis()));

                SyncDelta delta = builder.build();

                boolean buildOk = handler.handle(delta);

                if(buildOk){
                    LOG.info("Deu certo");
                }else{
                    LOG.error("não deu certo");
                }
            }
        }
    }

    public static final TipoConsulta FERIAS = new TipoConsulta(
            "ferias",
            "ConsultarListaDeModificadosPorFeriasResponse",
            "ConsultarListaDeModificadosPorFeriasResult",
            "ContratoDeTrabalhoModificadoPorFerias",
            "OperacaoDeFerias");

    public static final TipoConsulta AFASTAMENTO = new TipoConsulta(
            "afastamento",
            "ConsultarListaDeModificadosPorAfastamentoResponse",
            "ConsultarListaDeModificadosPorAfastamentoResult",
            "ContratoDeTrabalhoModificadoPorAfastamento",
            "OperacaoDeAfastamento");

    public static final TipoConsulta DEMISSAO = new TipoConsulta(
            "demissao",
            "ConsultarListaDeModificadosPorRescisaoResponse",
            "ConsultarListaDeModificadosPorRescisaoResult",
            "ContratoDeTrabalhoModificadoPorRescisao",
            "OperacaoDeRescisao");

    private ConnectorObject translate(UserRecord user, TipoConsulta consulta, String dataInicio, String dataFim) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.ACCOUNT);

        String name = user.matricula() + ";" + user.codigoEmpresa();

        addAttribute(builder, Name.NAME, name);
        addAttribute(builder, Uid.NAME, name);
        addAttribute(builder, USER_MATRICULA, user.matricula());
        addAttribute(builder, USER_NOME, user.nome());
        addAttribute(builder, USER_CARGO, user.cargo());
        addAttribute(builder, USER_EMPRESA, user.empresa());
        addAttribute(builder, USER_OU, user.organizacao());
        addAttribute(builder, USER_DATA_ADMISSAO, user.dataAdmissao());
        addAttribute(builder, USER_DATA_DE_NASCIMENTO, user.dataDeNascimento());
        addAttribute(builder, USER_CPF, user.cpf());
        if (user.dataDaRescisao() != null) {
            addAttribute(builder, USER_DATA_DE_RESCISAO, user.dataDaRescisao());
        }
        if (user.gestorMatricula() != null) {
            addAttribute(builder, MATRICULA_SUPERIOR, user.gestorMatricula());
        }
        if (user.email() != null) {
            addAttribute(builder, USER_EMAIL, user.email());
        }
        if (consulta != null) {
            if ("ferias".equals(consulta.tipo())) {
                addAttribute(builder, USER_DATA_INICIO_FERIAS, dataInicio);
                addAttribute(builder, USER_DATA_FIM_FERIAS, dataFim);
            } else if ("afastamento".equals(consulta.tipo())) {
                addAttribute(builder, USER_DATA_INICIO_AFASTAMENTO, dataInicio);
                addAttribute(builder, USER_DATA_FIM_AFASTAMENTO, dataFim);
            }
        }
        addAttribute(builder, USER_IS_PRIMARY, user.isPrimary());

        return builder.build();
    }

    public ObjectClassInfoBuilder userSchemaBuilder() {
        ObjectClassInfoBuilder objectClassInfoBuilder = new ObjectClassInfoBuilder();
        objectClassInfoBuilder.setType(ObjectClass.ACCOUNT_NAME);

        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        Name.NAME,
                        String.class,
                        Name.NAME,
                        AttributeInfo.Flags.REQUIRED
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        Uid.NAME,
                        String.class,
                        Uid.NAME,
                        AttributeInfo.Flags.REQUIRED
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_MATRICULA,
                        String.class,
                        USER_MATRICULA
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_NOME,
                        String.class,
                        USER_NOME
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
                        String.class,
                        USER_DATA_ADMISSAO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_DE_NASCIMENTO,
                        String.class,
                        USER_DATA_DE_NASCIMENTO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_DE_RESCISAO,
                        String.class,
                        USER_DATA_DE_RESCISAO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        MATRICULA_SUPERIOR,
                        String.class,
                        MATRICULA_SUPERIOR
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_CPF,
                        String.class,
                        USER_CPF
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_INICIO_FERIAS,
                        String.class,
                        USER_DATA_INICIO_FERIAS
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_FIM_FERIAS,
                        String.class,
                        USER_DATA_FIM_FERIAS
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_INICIO_AFASTAMENTO,
                        String.class,
                        USER_DATA_INICIO_AFASTAMENTO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_DATA_FIM_AFASTAMENTO,
                        String.class,
                        USER_DATA_FIM_AFASTAMENTO
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_EMAIL,
                        String.class,
                        USER_EMAIL
                )
        );
        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        USER_IS_PRIMARY,
                        Boolean.class,
                        USER_IS_PRIMARY
                )
        );
        return objectClassInfoBuilder;
    }
}
