package org.mifos.connector.gsma.camel.config;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.camel.component.http.HttpClientConfigurer;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
            sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {

                public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    return true;
                }
            }).build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            logger.debug(e.getMessage());
        }
        clientBuilder.setSslcontext(sslContext);

        // don't check Hostnames, either.
        // -- use SSLConnectionSocketFactory.getDefaultHostnameVerifier(), if you don't want to weaken
        HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

        // here's the special part:
        // -- need to create an SSL Socket Factory, to use our weakened "trust strategy";
        // -- and create a Registry, to register it.
        //
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslSocketFactory).build();

        // now, we create connection-manager using our Registry.
        // -- allows multi-threaded use
        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        clientBuilder.setConnectionManager(connMgr);
    }

}
