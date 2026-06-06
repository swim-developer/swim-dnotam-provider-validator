package com.github.swim_developer.validator.dnotam.provider.infrastructure.client;

import com.github.swim_developer.validator.dnotam.provider.domain.model.HttpResult;
import com.github.swim_developer.validator.dnotam.provider.domain.port.in.ConformanceHttpPort;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@ApplicationScoped
public class ConformanceHttpClient implements ConformanceHttpPort {

    private final Vertx vertx;
    private final Optional<String> keystorePath;
    private final Optional<String> keystorePassword;
    private final String keystoreType;
    private final Optional<String> truststorePath;
    private final Optional<String> truststorePassword;
    private final String truststoreType;

    private WebClient webClient;

    @Inject
    public ConformanceHttpClient(
            Vertx vertx,
            @ConfigProperty(name = "proxy.mtls.keystore.path") Optional<String> keystorePath,
            @ConfigProperty(name = "proxy.mtls.keystore.password") Optional<String> keystorePassword,
            @ConfigProperty(name = "proxy.mtls.keystore.type", defaultValue = "PKCS12") String keystoreType,
            @ConfigProperty(name = "proxy.mtls.truststore.path") Optional<String> truststorePath,
            @ConfigProperty(name = "proxy.mtls.truststore.password") Optional<String> truststorePassword,
            @ConfigProperty(name = "proxy.mtls.truststore.type", defaultValue = "PKCS12") String truststoreType) {
        this.vertx = vertx;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.keystoreType = keystoreType;
        this.truststorePath = truststorePath;
        this.truststorePassword = truststorePassword;
        this.truststoreType = truststoreType;
    }

    @PostConstruct
    void init() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(30000)
                .setIdleTimeout(30);

        boolean hasMtls = keystorePath.filter(p -> !p.isBlank()).isPresent()
                && truststorePath.filter(p -> !p.isBlank()).isPresent();

        if (hasMtls) {
            try {
                configureMtls(options);
                webClient = WebClient.create(vertx, options);
                log.info("ConformanceHttpClient initialized with mTLS");
            } catch (Exception e) {
                log.error("Failed to initialize mTLS", e);
                webClient = WebClient.create(vertx, options);
            }
        } else {
            webClient = WebClient.create(vertx, options);
            log.info("ConformanceHttpClient initialized without mTLS");
        }
    }

    private void configureMtls(WebClientOptions options) {
        String ksPath = keystorePath.get();
        String ksPass = keystorePassword.orElse("");
        String tsPath = truststorePath.get();
        String tsPass = truststorePassword.orElse("");

        options.setSsl(true).setTrustAll(false).setVerifyHost(false);

        if ("PKCS12".equalsIgnoreCase(keystoreType)) {
            options.setKeyCertOptions(new PfxOptions().setPath(ksPath).setPassword(ksPass));
        } else {
            options.setKeyCertOptions(new JksOptions().setPath(ksPath).setPassword(ksPass));
        }

        if ("PKCS12".equalsIgnoreCase(truststoreType)) {
            options.setTrustOptions(new PfxOptions().setPath(tsPath).setPassword(tsPass));
        } else {
            options.setTrustOptions(new JksOptions().setPath(tsPath).setPassword(tsPass));
        }
    }

    public HttpResult executePost(String url, String authToken, String body) {
        try {
            CompletableFuture<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> future = new CompletableFuture<>();
            webClient.postAbs(url)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Authorization", "Bearer " + authToken)
                    .sendBuffer(io.vertx.core.buffer.Buffer.buffer(body), ar -> {
                        if (ar.succeeded()) future.complete(ar.result());
                        else future.completeExceptionally(ar.cause());
                    });
            var r = future.get(30, TimeUnit.SECONDS);
            return new HttpResult(r.statusCode(), r.bodyAsString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("POST request interrupted", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("POST request failed: " + c.getMessage(), c);
        } catch (TimeoutException e) {
            throw new IllegalStateException("POST request timed out", e);
        }
    }

    public HttpResult executeGet(String url, String authToken) {
        try {
            CompletableFuture<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>> future = new CompletableFuture<>();
            webClient.getAbs(url)
                    .putHeader("Authorization", "Bearer " + authToken)
                    .send(ar -> {
                        if (ar.succeeded()) future.complete(ar.result());
                        else future.completeExceptionally(ar.cause());
                    });
            var r = future.get(30, TimeUnit.SECONDS);
            return new HttpResult(r.statusCode(), r.bodyAsString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GET request interrupted", e);
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("GET request failed: " + c.getMessage(), c);
        } catch (TimeoutException e) {
            throw new IllegalStateException("GET request timed out", e);
        }
    }
}
