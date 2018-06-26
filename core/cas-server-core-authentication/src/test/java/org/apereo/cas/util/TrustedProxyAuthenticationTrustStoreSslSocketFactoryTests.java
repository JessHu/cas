package org.apereo.cas.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apereo.cas.authentication.DefaultCasSslContext;
import org.apereo.cas.util.http.HttpClient;
import org.apereo.cas.util.http.SimpleHttpClientFactoryBean;
import org.junit.Test;
import org.junit.Before;
import org.springframework.core.io.ClassPathResource;

import java.security.KeyStore;

import static org.junit.Assert.*;

/**
 * @author Misagh Moayyed
 * @since 4.1.0
 */
@Slf4j
public class TrustedProxyAuthenticationTrustStoreSslSocketFactoryTests {
    private static final ClassPathResource TRUST_STORE = new ClassPathResource("truststore.jks");
    private static final String TRUST_STORE_PSW = "changeit";

    private HttpClient client;

    @Before
    public void prepareHttpClient() {
        final var clientFactory = new SimpleHttpClientFactoryBean();
        clientFactory.setSslSocketFactory(new SSLConnectionSocketFactory(
                new DefaultCasSslContext(TRUST_STORE, TRUST_STORE_PSW, KeyStore.getDefaultType()).getSslContext()));
        this.client = clientFactory.getObject();
    }

    @Test
    public void verifySuccessfulConnection() {
        final var valid = client.isValidEndPoint("https://www.github.com");
        assertTrue(valid);
    }

    @Test
    public void verifySuccessfulConnectionWithCustomSSLCert() {
        final var valid = client.isValidEndPoint("https://self-signed.badssl.com");
        assertTrue(valid);
    }

}
