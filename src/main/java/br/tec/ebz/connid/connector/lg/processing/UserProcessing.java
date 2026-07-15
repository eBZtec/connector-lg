package br.tec.ebz.connid.connector.lg.processing;

import br.tec.ebz.connid.connector.lg.*;
import br.tec.ebz.connid.connector.lg.model.UserRecord;
import com.evolveum.polygon.common.GuardedStringAccessor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.objects.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.xml.XmlMapper;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
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

    public void search(String uid, ResultsHandler resultsHandler) throws Exception {
        if (uid == null) {
            searchAllUsers(resultsHandler);
        } else {
            searchUserById(uid, resultsHandler);
        }
    }

    public void searchUserById(String uid, ResultsHandler resultsHandler) throws Exception {}

    public void searchAllUsers(ResultsHandler resultsHandler) throws Exception {
        int pageNumber = 1;
        PageResult page;

        do {
            page = fetchUserPage(pageNumber);
            for (UserRecord user : page.users()) {
                resultsHandler.handle(translate(user));
            }
            pageNumber++;

        } while (pageNumber <= page.totalPages());
    }

    private PageResult fetchUserPage(int page) {

        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost post = buildUserRequest(url, page);

            try (CloseableHttpResponse response = client.execute(post)) {

                String responseXml = EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8);

                XmlMapper mapper = new XmlMapper();

                JsonNode root = mapper.readTree(responseXml);

                JsonNode result = root.get("Body").get("ConsultarContratosAplicandoAutorizacaoDePerfilResponse").get("ConsultarContratosAplicandoAutorizacaoDePerfilResult");

                int totalPages = result.path("TotalDePaginas").asInt();

                JsonNode retorno = result.get("Retorno");
                JsonNode contratos = retorno.path("ContratoDeTrabalhoV3");

                List<UserRecord> users = new ArrayList<>();

                for (JsonNode contrato : contratos) {
                    users.add(new UserRecord(
                            contrato.path("Alocacao").path("CargoEFuncaoEfetivo").path("Cargo").path("Descricao").asText(),
                            contrato.path("Alocacao").path("Empresa").path("Descricao").asText(),
                            contrato.path("Alocacao").path("UnidadeOrganizacional").path("Descricao").asText(),
                            contrato.path("Pessoa").path("Contato").path("EmailCorporativo").asText(),
                            contrato.path("Pessoa").path("Contato").path("EmailParticular").asText(),
                            contrato.path("DadosGerais").path("Matricula").asText(),
                            contrato.path("Pessoa").path("DadosPessoais").path("Nome").asText(),
                            contrato.path("Pessoa").path("DadosPessoais").path("DataDeNascimento").asText(),
                            contrato.path("DadosGerais").path("DataAdmissao").asText(),
                            contrato.path("DadosDaRescisao").path("DataDaRescisao").asText()
                    ));
                }

                return new PageResult(users, totalPages);
            }
        } catch (Exception e) {
            throw new ConnectionFailedException("Failed to fetch page " + page, e);
        }
    }

    private record PageResult(List<UserRecord> users, int totalPages) {}

    private String getPasswordAsString() {
        GuardedStringAccessor accessor = new GuardedStringAccessor();
        GuardedString systemKey = configuration.getSenha();
        systemKey.access(accessor);
        return accessor.getClearString();
    }

    private HttpPost buildUserRequest(String url, int totalDePaginas) {
        HttpPost post = new HttpPost(url);
        String xml = String.format("""
                        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:dto="lg.com.br/svc/dto" xmlns:v2="lg.com.br/api/v2" xmlns:v1="lg.com.br/api/dto/v1" xmlns:arr="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
                           <soapenv:Header>
                              <dto:LGAutenticacao>
                                 <dto:TokenUsuario>
                                    <dto:Senha>%s</dto:Senha>
                                    <dto:Usuario>%s</dto:Usuario>
                                    <dto:GuidTenant>%s</dto:GuidTenant>
                                 </dto:TokenUsuario>
                              </dto:LGAutenticacao>
                              <dto:LGContextoAmbiente>
                                 <dto:Ambiente>%s</dto:Ambiente>
                              </dto:LGContextoAmbiente>
                           </soapenv:Header>
                           <soapenv:Body>
                              <v2:ConsultarContratosAplicandoAutorizacaoDePerfil>
                                 <v2:filtro>
                                    <v1:PaginaAtual>%d</v1:PaginaAtual>
                                    <v1:TiposDeSituacoes>
                                       <arr:int>1</arr:int>
                                       <arr:int>2</arr:int>
                                       <arr:int>3</arr:int>
                                       <arr:int>4</arr:int>
                                       <arr:int>5</arr:int>
                                    </v1:TiposDeSituacoes>
                                 </v2:filtro>
                              </v2:ConsultarContratosAplicandoAutorizacaoDePerfil>
                           </soapenv:Body>
                        </soapenv:Envelope>
                        """,
                getPasswordAsString(),
                configuration.getUsuario(),
                configuration.getGuidtenant(),
                configuration.getAmbiente(),
                totalDePaginas
        );
        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarContratosAplicandoAutorizacaoDePerfil");

        return post;
    }

    private ConnectorObject translate(UserRecord user) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.ACCOUNT);

        addAttribute(builder, Name.NAME, user.nome());
        addAttribute(builder, Uid.NAME, user.matricula());
        addAttribute(builder, USER_CARGO, user.cargo());
        addAttribute(builder, USER_EMPRESA, user.empresa());
        addAttribute(builder, USER_OU, user.organizacao());
        addAttribute(builder, USER_DATA_ADMISSAO, user.dataAdmissao());
        addAttribute(builder, USER_EMAIL_CORPORATIVO, user.emailCorporativo());
        addAttribute(builder, USER_EMAIL_PATICULAR, user.emailParticular());
        addAttribute(builder, USER_DATA_DE_NASCIMENTO, user.dataDeNascimento());
        addAttribute(builder, USER_DATA_DE_RESCISAO, user.dataDaRescisao());

        return builder.build();
    }

    public ObjectClassInfoBuilder userSchemaBuilder() {
        ObjectClassInfoBuilder objectClassInfoBuilder = new ObjectClassInfoBuilder();
        objectClassInfoBuilder.setType(ObjectClass.ACCOUNT_NAME);

        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        Uid.NAME,
                        String.class,
                        USER_Matricula,
                        AttributeInfo.Flags.REQUIRED
                )
        );

        objectClassInfoBuilder.addAttributeInfo(
                buildAttributeInfo(
                        Name.NAME,
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
        return objectClassInfoBuilder;
    }
}
