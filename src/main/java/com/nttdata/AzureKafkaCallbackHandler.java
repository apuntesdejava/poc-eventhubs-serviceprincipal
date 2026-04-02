package com.nttdata;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import org.apache.kafka.common.security.auth.AuthenticateCallbackHandler;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerToken;
import org.apache.kafka.common.security.oauthbearer.OAuthBearerTokenCallback;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AzureKafkaCallbackHandler implements AuthenticateCallbackHandler {
    private ClientSecretCredential credential;
    private String clientId;
    private String azureScope;
    private static final Logger LOGGER = Logger.getLogger(AzureKafkaCallbackHandler.class);

    public AzureKafkaCallbackHandler() {
        LOGGER.info(">>> AZURE_AUTH: Instanciando AzureKafkaCallbackHandler...");
    }

    @Override
    public void configure(Map<String, ?> configs, String saslMechanism, List<AppConfigurationEntry> jaasConfigEntries) {
        // Leemos directamente de la configuración de Quarkus
        this.clientId = ConfigProvider.getConfig().getValue("azure.client.id", String.class);
        String clientSecret = ConfigProvider.getConfig().getValue("azure.client.secret", String.class);
        String tenantId = ConfigProvider.getConfig().getValue("azure.tenant.id", String.class);

        // El scope debe apuntar al namespace específico del Event Hub, no al endpoint genérico de Service Bus.
        // Formato requerido por Azure Event Hubs: https://<namespace>.servicebus.windows.net/.default
        this.azureScope = ConfigProvider.getConfig()
                .getOptionalValue("azure.scope", String.class)
                .orElseGet(() -> {
                    // Derivamos el scope del bootstrap server configurado
                    String bootstrapServer = ConfigProvider.getConfig()
                            .getValue("kafka.bootstrap.servers", String.class);
                    // El bootstrap.servers tiene la forma "host:port" — extraemos solo el host
                    String host = bootstrapServer.split(":")[0];
                    return "https://" + host + "/.default";
                });

        this.credential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        LOGGER.info(">>> AZURE_AUTH: Handler configurado correctamente para ClientID: " + clientId + ", scope: " + azureScope);
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof OAuthBearerTokenCallback oauthCallback) {
                try {
                    LOGGER.info(">>> AZURE_AUTH: Solicitando token a Azure AD...");
                    AccessToken token = credential.getToken(new TokenRequestContext().addScopes(azureScope)).block();
                    if (token != null) {
                        LOGGER.info(">>> AZURE_AUTH: Token obtenido con éxito. Expira: " + token.getExpiresAt());
                        oauthCallback.token(new AzureOAuthBearerToken(token.getToken(), 
                                token.getExpiresAt().toInstant().toEpochMilli(), 
                                clientId));
                    } else {
                        throw new IOException("El token obtenido es nulo");
                    }
                } catch (Exception e) {
                    LOGGER.error(">>> AZURE_AUTH_ERROR: " + e.getMessage(),e);
                    throw new IOException("Error obteniendo token de Azure AD", e);
                }
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }

    @Override
    public void close() {}

    private static class AzureOAuthBearerToken implements OAuthBearerToken {
        private final String token;
        private final long lifetimeMs;
        private final String principalName;

        public AzureOAuthBearerToken(String token, long lifetimeMs, String principalName) {
            this.token = token;
            this.lifetimeMs = lifetimeMs;
            this.principalName = (principalName != null) ? principalName : "azure-sp";
        }

        @Override
        public String value() { return token; }
        @Override
        public Set<String> scope() { return Collections.emptySet(); }
        @Override
        public long lifetimeMs() { return lifetimeMs; }
        @Override
        public String principalName() { return principalName; }
        @Override
        public Long startTimeMs() { return System.currentTimeMillis(); }
    }
}
