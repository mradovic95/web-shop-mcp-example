package com.example.webshop.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.McpDefaultJwtCustomizer;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2ClientRegistration;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientRegistrationAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.converter.OAuth2ClientRegistrationRegisteredClientConverter;
import org.springframework.security.oauth2.server.authorization.mcp.token.ResourceIdentifierAudienceTokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
public class SecurityConfig {

    private static final String MCP_PATH = "/mcp";
    private static final String RESOURCE_METADATA_PATH = "/.well-known/oauth-protected-resource";
    private static final String RESOURCE_PARAM = "resource";
    private static final StringKeyGenerator REFRESH_TOKEN_KEYS =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    @Value("${webshop.oauth2.issuer}")
    private String issuer;

    @Value("${webshop.oauth2.jwk-file}")
    private String jwkFile;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2AuthorizationConsentService authorizationConsentService) throws Exception {
        http.with(McpAuthorizationServerConfigurer.mcpAuthorizationServer(), mcp -> mcp
                .dynamicClientRegistration(true)
                .authorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer
                            .authorizationServerSettings(authorizationServerSettings())
                            .registeredClientRepository(registeredClientRepository)
                            .authorizationService(authorizationService)
                            .authorizationConsentService(authorizationConsentService)
                            .clientRegistrationEndpoint(registration -> registration
                                    .authenticationProviders(providers -> providers.forEach(provider -> {
                                        if (provider instanceof OAuth2ClientRegistrationAuthenticationProvider p) {
                                            p.setRegisteredClientConverter(dynamicClientConverter());
                                        }
                                    })))
                            .clientAuthentication(clientAuthentication -> clientAuthentication
                                    .authenticationConverter(new PublicClientRefreshTokenAuthenticationConverter())
                                    .authenticationProvider(
                                            new PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository)));
                }));
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
        entryPoint.setResourceMetadataParameterResolver(request -> issuer + RESOURCE_METADATA_PATH + MCP_PATH);
        return http
                .securityMatcher(MCP_PATH, MCP_PATH + "/**", RESOURCE_METADATA_PATH, RESOURCE_METADATA_PATH + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(RESOURCE_METADATA_PATH, RESOURCE_METADATA_PATH + "/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(entryPoint)
                        .protectedResourceMetadata(metadata -> metadata
                                .protectedResourceMetadataCustomizer(builder -> builder.authorizationServer(issuer))))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        Path file = Path.of(jwkFile);
        RSAKey rsaKey;
        if (Files.exists(file)) {
            rsaKey = RSAKey.parse(Files.readString(file));
        } else {
            rsaKey = generateRsaKey();
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, rsaKey.toJSONString());
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            }
            log.info("Generated new OAuth2 signing key at {}", file.toAbsolutePath());
        }
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private static RSAKey generateRsaKey() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        ResourceIdentifierAudienceTokenCustomizer resourceAudience = new ResourceIdentifierAudienceTokenCustomizer();
        jwtGenerator.setJwtCustomizer(context -> {
            McpDefaultJwtCustomizer.DEFAULT_JWT_CUSTOMIZER.customize(context);
            resourceAudience.customize(context);
            keepAudienceOnRefresh(context);
        });
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, SecurityConfig::generateRefreshToken);
    }

    private static OAuth2RefreshToken generateRefreshToken(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(REFRESH_TOKEN_KEYS.generateKey(), issuedAt, expiresAt);
    }

    private static void keepAudienceOnRefresh(JwtEncodingContext context) {
        if (!AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())
                || !OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                || !(context.getAuthorizationGrant() instanceof OAuth2AuthorizationGrantAuthenticationToken grant)
                || grant.getAdditionalParameters().get(RESOURCE_PARAM) != null
                || context.getAuthorization() == null) {
            return;
        }
        OAuth2Authorization.Token<OAuth2AccessToken> previous = context.getAuthorization().getAccessToken();
        if (previous != null && previous.getClaims() != null && previous.getClaims().get(JwtClaimNames.AUD) != null) {
            context.getClaims().claim(JwtClaimNames.AUD, previous.getClaims().get(JwtClaimNames.AUD));
        }
    }

    private static Converter<OAuth2ClientRegistration, RegisteredClient> dynamicClientConverter() {
        OAuth2ClientRegistrationRegisteredClientConverter delegate = new OAuth2ClientRegistrationRegisteredClientConverter();
        delegate.setTokenSettingsCustomizer(tokenSettings -> tokenSettings.reuseRefreshTokens(false));
        return registration -> {
            RegisteredClient client = delegate.convert(registration);
            if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)
                    && !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
                return RegisteredClient.from(client).authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).build();
            }
            return client;
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(issuer + MCP_PATH))));
        return jwtDecoder;
    }
}
