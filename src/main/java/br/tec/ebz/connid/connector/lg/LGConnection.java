package br.tec.ebz.connid.connector.lg;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.ssl.SSLContexts;
import org.identityconnectors.common.logging.Log;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.message.BasicHeader;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public class LGConnection {

    private static final Log LOG = Log.getLog(LGConnection.class);

    private LGConfiguration configuration;
    private final HttpClient httpClient;

    public LGConnection(LGConfiguration configuration){
        this.configuration = configuration;

        this.httpClient = HttpClients.custom()
            .setDefaultHeaders(List.of(new BasicHeader("Accept", "application/xml")))
            .build();

    }

    private static CloseableHttpClient getHttpClient()
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {

        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial((chain, authType) -> true)
                .build();

        var tlsStrategy = new DefaultClientTlsStrategy(
                sslContext,
                NoopHostnameVerifier.INSTANCE
        );

        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsStrategy)
                .build();

        return HttpClients.custom()
                .setDefaultHeaders(List.of(
                        new BasicHeader("content-type", "application/xml")
                ))
                .setConnectionManager(manager)
                .build();
    }

    public void dispose() {
        //todo implement
    }
}