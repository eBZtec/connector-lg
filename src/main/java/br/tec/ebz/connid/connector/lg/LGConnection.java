package br.tec.ebz.connid.connector.lg;

import com.evolveum.polygon.common.GuardedStringAccessor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.identityconnectors.common.logging.Log;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.message.BasicHeader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.xml.XmlMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class LGConnection {

    private static final Log LOG = Log.getLog(LGConnection.class);

    private LGConfiguration configuration;
    private final CloseableHttpClient httpClient;

    public LGConnection(LGConfiguration configuration) {
        this.configuration = configuration;

        this.httpClient = HttpClients.custom()
                .setDefaultHeaders(List.of(new BasicHeader("Accept", "application/xml")))
                .build();

        status();
    }

    private void status() {

        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost post = buildRequest(url, 1);

            try (CloseableHttpResponse response = client.execute(post)) {

                int status = response.getCode();

                if (status < 200 || status > 299) {
                    String responseXml = EntityUtils.toString(
                            response.getEntity(),
                            StandardCharsets.UTF_8);

                    XmlMapper mapper = new XmlMapper();
                    JsonNode root = mapper.readTree(responseXml);
                    String body = root.get("Body").get("Fault").get("faultstring").path("").asString();

                    LOG.error("Falha no teste de conexão. HTTP {0} - {1}", status, body);
                    throw new ConnectionFailedException(
                            "Teste de conexão falhou. HTTP " + status);
                }
                EntityUtils.consume(response.getEntity());
            }
        } catch (Exception e) {
            throw new ConnectionFailedException("Erro ao conectar com a API", e);
        }
    }

    public String getPasswordAsString() {
        GuardedStringAccessor accessor = new GuardedStringAccessor();
        GuardedString systemKey = configuration.getSenha();
        systemKey.access(accessor);
        return accessor.getClearString();
    }

    public HttpPost buildRequest(String url, int totalDePaginas) {
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

    public void dispose() {
        //todo implement
    }
}