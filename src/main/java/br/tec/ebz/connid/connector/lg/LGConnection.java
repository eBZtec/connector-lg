package br.tec.ebz.connid.connector.lg;

import com.evolveum.polygon.common.GuardedStringAccessor;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContexts;
import org.identityconnectors.common.logging.Log;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.message.BasicHeader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LGConnection {

    private static final Log LOG = Log.getLog(LGConnection.class);

    private LGConfiguration configuration;
    private final CloseableHttpClient httpClient;

    private final XmlMapper mapper;

    public LGConnection(LGConfiguration configuration) {
        this.configuration = configuration;

        this.mapper = new XmlMapper();
        var sslContext = SSLContexts.createSystemDefault();

        var tlsStrategy = new DefaultClientTlsStrategy(
                sslContext,
                HostnameVerificationPolicy.CLIENT,
                NoopHostnameVerifier.INSTANCE
        );

        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsStrategy)
                .build();

        this.httpClient = HttpClients.custom()
                .setDefaultHeaders(List.of(
                        new BasicHeader("Accept", "application/xml")))
                .setConnectionManager(manager)
                .build();

        status();
    }

    private void status() {

        String url = configuration.getUrlEndpoint() + "/v2/servicodecontratodetrabalho";

        try {
            findStatus(url);

            LOG.info("Teste de conexão com LG realizado com sucesso.");
        } catch (Exception e) {
            throw new ConnectionFailedException("Erro ao conectar com a API", e);
        }
    }

    public String findStatus(String url) throws IOException{
        HttpPost post = new HttpPost(url);
        String body = """ 
                           <soapenv:Body>
                           </soapenv:Body>
                        """;

        String xml = headler(body);

        StringEntity entity = new StringEntity(xml, StandardCharsets.UTF_8);
        post.setEntity(entity);

        post.setHeader("Content-Type", "text/xml; charset=UTF-8");
        post.setHeader("Accept", "text/xml");
        post.setHeader("SOAPAction", "lg.com.br/api/v2/ServicoDeContratoDeTrabalho/ConsultarContratosAplicandoAutorizacaoDePerfil");

        return postRequest(post,"Teste de conexão falhou");
    }

    public String getPasswordAsString() {
        GuardedStringAccessor accessor = new GuardedStringAccessor();
        GuardedString systemKey = configuration.getSenha();
        systemKey.access(accessor);
        return accessor.getClearString();
    }

    public String headler(String body){
        return String.format("""
                        <soapenv:Envelope
                                    xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                    xmlns:dto="lg.com.br/svc/dto"
                                    xmlns:v1="lg.com.br/api/dto/v1"
                                    xmlns:v2="lg.com.br/api/v2"
                                    xmlns:v11="lg.com.br/api/dto/v1"
                                    xmlns:arr="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
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
                            %s
                        </soapenv:Envelope>
                        """,
                getPasswordAsString(),
                configuration.getUsuario(),
                configuration.getGuidtenant(),
                configuration.getAmbiente(),
                body
        );
    }

    public String postRequest(HttpPost post, String mensagemErro) throws IOException {
        return retornaHttpPost(post, response -> {
            int status = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (status < 200 || status > 299) {
                JsonNode root = mapper.readTree(responseBody);
                String mensagem = root.get("Body").get("Fault").get("faultstring").path("").asString();
                LOG.error("Post FALHOU: status {1}, body: {2}", status, mensagem);
                throw new ConnectionFailedException( mensagemErro + " : " + mensagem);
            }

            return responseBody;
        });
    }

    public String retornaHttpPost(HttpPost post, HttpClientResponseHandler<String> handler) throws IOException {
        return httpClient.execute(post, handler);
    }

    public void dispose() {
        //todo implement
    }
}