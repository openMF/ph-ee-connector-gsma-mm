package org.mifos.connector.gsma.camel.config;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Ported from Apache HttpClient 4 to HttpClient 5: camel-http in Camel 4 passes
// an HC5 HttpClientBuilder to HttpClientConfigurer. Behaviour is unchanged
// (trust all certificates, no hostname verification).
@Component
public class HttpClientConfigurerTrustAllCACerts implements HttpClientConfigurer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public HttpClientConfigurerTrustAllCACerts() {}

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        // setup a Trust Strategy that allows all certificates.
        //
        SSLContext sslContext = null;
        try {
            sslContext = SSLContextBuilder.create().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            logger.debug(e.getMessage());
        }

        // don't check Hostnames, either, and use our weakened trust strategy.
        // In HttpClient 5 the TLS configuration travels with the connection
        // manager instead of the client builder.
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        PoolingHttpClientConnectionManager connMgr = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory).build();
        clientBuilder.setConnectionManager(connMgr);
    }

}
