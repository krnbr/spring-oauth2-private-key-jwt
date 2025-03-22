package in.neuw.spring.oauth2;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import java.text.ParseException;
import java.util.function.Function;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.PS256;

@Configuration
public class Oauth2ClientConfig {

    private final RestClient defaultRestClient;

    public Oauth2ClientConfig() {
        this.defaultRestClient = RestClient.builder()
                .requestInterceptor(new LoggingInterceptor("OAUTH2"))
                // default one has no custom ssl customization, etc.
                // .requestFactory(clientHttpRequestFactory())
                .messageConverters((messageConverters) -> {
                    messageConverters.clear();
                    messageConverters.add(new FormHttpMessageConverter());
                    messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
    }

    /**
     * This is needed as we do not want Spring security to render the login form,
     * in the case here, we just want to consume services protected by OAUTH2
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity.build();
    }

    @Bean
    public RestClient oauth2RestClient(final ClientRegistrationRepository clientRegistrationRepository,
                                       @Value("${private-jwt-key}") final String privateKey,
                                       @Value("${cert-thumbprint}") final String certThumbprint,
                                       @Value("${downstream.base-path}") final String downstreamBasePath) {
        var restClientClientCredentialsTokenResponseClient = getRestClientClientCredentialsTokenResponseClient(privateKey, certThumbprint);

        var provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials(c -> c.accessTokenResponseClient(restClientClientCredentialsTokenResponseClient))
                .build();

        var oauth2AuthorizedClientService = new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

        var oAuth2AuthorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oauth2AuthorizedClientService);
        oAuth2AuthorizedClientManager.setAuthorizedClientProvider(provider);

        var oAuth2ClientHttpRequestInterceptor = new OAuth2ClientHttpRequestInterceptor(oAuth2AuthorizedClientManager);

        return RestClient.builder()
                //.requestInterceptor(oAuth2ClientHttpRequestInterceptor)
                .requestInterceptors(c -> {
                    c.add(oAuth2ClientHttpRequestInterceptor);
                    c.add(new LoggingInterceptor("DOWNSTREAM"));
                })
                //.requestInterceptor(new ClientLoggerRequestInterceptor())
                .baseUrl(downstreamBasePath)
                .defaultRequest(d -> {
                    // the id of the client, is entra in my case in application properties change accordingly
                    d.attributes(clientRegistrationId("entra"));
                })
                .build();
    }

    private RestClientClientCredentialsTokenResponseClient getRestClientClientCredentialsTokenResponseClient(final String privateKey,
                                                                                                             final String certThumbprint) {
        var nimbusJwtConverter = getNimbusJwtClientAuthenticationParametersConverter(privateKey, certThumbprint);

        var restClientClientCredentialsTokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
        restClientClientCredentialsTokenResponseClient.setRestClient(this.defaultRestClient);
        restClientClientCredentialsTokenResponseClient.addParametersConverter(nimbusJwtConverter);
        return restClientClientCredentialsTokenResponseClient;
    }

    private NimbusJwtClientAuthenticationParametersConverter<OAuth2ClientCredentialsGrantRequest> getNimbusJwtClientAuthenticationParametersConverter(String privateKey, String certThumbprint) {
        var nimbusJwtConverter = new NimbusJwtClientAuthenticationParametersConverter<OAuth2ClientCredentialsGrantRequest>(jwkResolver(privateKey));
        nimbusJwtConverter.setJwtClientAssertionCustomizer((c) -> {
            c.getHeaders()
                    // Base64url-encoded SHA-256 thumbprint of the X.509 certificate's DER encoding, in case of example here
                    // this may not be the requirement for all!
                    // example - this is needed with entra id!
                    .header("x5t", certThumbprint)
                    .algorithm(PS256)
                    .build();
        });
        return nimbusJwtConverter;
    }

    private Function<ClientRegistration, JWK> jwkResolver(final String privateKey) {
        return (cr) -> {
            try {
                // can use alternative parsing techniques, used the simplest one, based on com.nimbusds.jose.jwk.JWK
                // based on file etc.
                // here it is JWK based
                return JWK.parse(privateKey);
            } catch (ParseException e) {
                // break the boot of app!
                throw new RuntimeException(e);
            }
        };
    }

}
