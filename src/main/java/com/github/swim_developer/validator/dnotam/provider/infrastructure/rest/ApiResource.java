package com.github.swim_developer.validator.dnotam.provider.infrastructure.rest;

import com.github.swim_developer.validator.provider.infrastructure.rest.dto.KeycloakConfig;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.ProviderConfig;
import com.github.swim_developer.validator.provider.infrastructure.rest.dto.StatusInfo;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

@Slf4j
@Path("/api")
public class ApiResource {

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @ConfigProperty(name = "keycloak.realm")
    String keycloakRealm;

    @ConfigProperty(name = "keycloak.client-id")
    String keycloakClientId;

    @ConfigProperty(name = "swim.provider.api.urls")
    List<String> swimProviderApiUrls;

    @ConfigProperty(name = "proxy.mtls.keystore.path")
    Optional<String> keystorePath;

    @ConfigProperty(name = "proxy.mtls.truststore.path")
    Optional<String> truststorePath;

    @GET
    @Path("/config/keycloak")
    @Produces(MediaType.APPLICATION_JSON)
    public KeycloakConfig getKeycloakConfig() {
        return new KeycloakConfig(keycloakUrl, keycloakRealm, keycloakClientId);
    }

    @GET
    @Path("/config/provider")
    @Produces(MediaType.APPLICATION_JSON)
    public ProviderConfig getProviderConfig() {
        return new ProviderConfig(swimProviderApiUrls);
    }

    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public StatusInfo getStatus() {
        boolean mtlsEnabled = keystorePath.filter(p -> !p.isBlank()).isPresent()
                && truststorePath.filter(p -> !p.isBlank()).isPresent();

        return new StatusInfo(
                mtlsEnabled,
                mtlsEnabled ? keystorePath.orElse("") : null,
                mtlsEnabled ? truststorePath.orElse("") : null,
                true,
                keycloakUrl,
                keycloakRealm
        );
    }
}
