package com.github.swim_developer.validator.dnotam.provider.infrastructure.messaging;

import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PfxOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
public class AmqpSslConfigurator {

    @ConfigProperty(name = "proxy.mtls.keystore.path")
    Optional<String> keystorePath;

    @ConfigProperty(name = "proxy.mtls.keystore.password")
    Optional<String> keystorePassword;

    @ConfigProperty(name = "proxy.mtls.keystore.type", defaultValue = "PKCS12")
    String keystoreType;

    @ConfigProperty(name = "proxy.mtls.truststore.path")
    Optional<String> truststorePath;

    @ConfigProperty(name = "proxy.mtls.truststore.password")
    Optional<String> truststorePassword;

    @ConfigProperty(name = "proxy.mtls.truststore.type", defaultValue = "PKCS12")
    String truststoreType;

    public void configure(AmqpClientOptions options) {
        options.setHostnameVerificationAlgorithm("");

        truststorePath.filter(p -> !p.isBlank()).ifPresent(path -> {
            String pwd = truststorePassword.orElse("");
            if ("JKS".equalsIgnoreCase(truststoreType)) {
                options.setTrustStoreOptions(new JksOptions().setPath(path).setPassword(pwd));
            } else {
                options.setPfxTrustOptions(new PfxOptions().setPath(path).setPassword(pwd));
            }
        });

        keystorePath.filter(p -> !p.isBlank()).ifPresent(path -> {
            String pwd = keystorePassword.orElse("");
            if ("JKS".equalsIgnoreCase(keystoreType)) {
                options.setKeyStoreOptions(new JksOptions().setPath(path).setPassword(pwd));
            } else {
                options.setPfxKeyCertOptions(new PfxOptions().setPath(path).setPassword(pwd));
            }
        });
    }
}
