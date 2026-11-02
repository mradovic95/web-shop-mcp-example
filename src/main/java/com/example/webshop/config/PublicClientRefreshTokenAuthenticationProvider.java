package com.example.webshop.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@RequiredArgsConstructor
public class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository registeredClientRepository;

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2ClientAuthenticationToken clientAuthentication = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())
                || !AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(
                        clientAuthentication.getAdditionalParameters().get(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(
                clientAuthentication.getPrincipal().toString());
        if (registeredClient == null
                || !registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
