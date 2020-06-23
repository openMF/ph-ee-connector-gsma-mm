package org.mifos.connector.gsma.zeebe;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeClientConfiguration {

    @Value("${zeebe.broker.contactpoint}")
    private String zeebeBrokerContactpoint;

    @Value("${zeebe.client.max-execution-threads}")
    private int zeebeClientMaxThreads;

    @Value("${zeebe.broker.audience}")
    private String zeebeAudience;

    @Value("${zeebe.broker.clientId}")
    private String zeebeClientId;

    @Value("${zeebe.broker.clientSecret}")
    private String zeebeClientSecret;

    @Bean
    public ZeebeClient setup() {
        OAuthCredentialsProvider cred = new OAuthCredentialsProviderBuilder()
                .audience(zeebeAudience)
                .clientId(zeebeClientId)
                .clientSecret(zeebeClientSecret)
                .build();

        return ZeebeClient.newClientBuilder()
                .brokerContactPoint(zeebeBrokerContactpoint)
                .credentialsProvider(cred)
//                .usePlaintext()
                .numJobWorkerExecutionThreads(zeebeClientMaxThreads)
                .build();
    }
}
