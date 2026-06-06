package com.github.swim_developer.validator.dnotam.provider.infrastructure.client;

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
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@ApplicationScoped
public class ProviderHttpClient {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final Vertx vertx;
    private final Optional<String> keystorePath;
    private final Optional<String> keystorePassword;
    private final String keystoreType;
    private final Optional<String> truststorePath;
    private final Optional<String> truststorePassword;
    private final String truststoreType;

    private WebClient webClient;

    @Inject
    public ProviderHttpClient(
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
                log.info("ProviderHttpClient initialized with mTLS");
            } catch (Exception e) {
                log.error("Failed to initialize mTLS client", e);
                webClient = WebClient.create(vertx, options);
            }
        } else {
            webClient = WebClient.create(vertx, options);
            log.info("ProviderHttpClient initialized without mTLS");
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

        log.info("mTLS configured: keystore={}, truststore={}", ksPath, tsPath);
    }

    public HttpResponse<Buffer> executeGet(String url, String token)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<HttpResponse<Buffer>> future = new CompletableFuture<>();
        webClient.getAbs(url)
                .putHeader(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
                .send()
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);
        return future.get(30, TimeUnit.SECONDS);
    }

    public HttpResponse<Buffer> executePost(String url, String token, String body)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<HttpResponse<Buffer>> future = new CompletableFuture<>();
        webClient.postAbs(url)
                .putHeader(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
                .putHeader("Content-Type", CONTENT_TYPE_JSON)
                .sendBuffer(Buffer.buffer(body != null ? body : ""))
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);
        return future.get(30, TimeUnit.SECONDS);
    }

    public HttpResponse<Buffer> executePut(String url, String token, String body)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<HttpResponse<Buffer>> future = new CompletableFuture<>();
        webClient.putAbs(url)
                .putHeader(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
                .putHeader("Content-Type", CONTENT_TYPE_JSON)
                .sendBuffer(Buffer.buffer(body != null ? body : ""))
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);
        return future.get(30, TimeUnit.SECONDS);
    }

    public HttpResponse<Buffer> executeDelete(String url, String token)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<HttpResponse<Buffer>> future = new CompletableFuture<>();
        webClient.deleteAbs(url)
                .putHeader(HEADER_AUTHORIZATION, BEARER_PREFIX + token)
                .send()
                .onSuccess(future::complete)
                .onFailure(future::completeExceptionally);
        return future.get(30, TimeUnit.SECONDS);
    }

    public Response buildResponse(HttpResponse<Buffer> response) {
        return Response.status(response.statusCode())
                .entity(Map.of(
                        "status", response.statusCode(),
                        "contentType", response.getHeader("content-type") != null
                                ? response.getHeader("content-type") : CONTENT_TYPE_JSON,
                        "body", response.bodyAsString() != null ? response.bodyAsString() : ""
                ))
                .build();
    }
}
