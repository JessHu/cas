package org.apereo.cas.authentication.trigger;

import org.apereo.cas.authentication.Authentication;
import org.apereo.cas.authentication.AuthenticationException;
import org.apereo.cas.authentication.MultifactorAuthenticationProvider;
import org.apereo.cas.authentication.MultifactorAuthenticationProviderResolver;
import org.apereo.cas.authentication.MultifactorAuthenticationTrigger;
import org.apereo.cas.authentication.MultifactorAuthenticationUtils;
import org.apereo.cas.authentication.adaptive.geo.GeoLocationService;
import org.apereo.cas.authentication.principal.Service;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.util.spring.ApplicationContextProvider;
import org.apereo.cas.web.support.WebUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apereo.inspektr.common.web.ClientInfoHolder;
import org.springframework.core.Ordered;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * This is {@link AdaptiveMultifactorAuthenticationTrigger}.
 *
 * @author Misagh Moayyed
 * @since 6.0.0
 */
@Getter
@Setter
@Slf4j
@RequiredArgsConstructor
public class AdaptiveMultifactorAuthenticationTrigger implements MultifactorAuthenticationTrigger {
    private final GeoLocationService geoLocationService;
    private final CasConfigurationProperties casProperties;
    private final MultifactorAuthenticationProviderResolver multifactorAuthenticationProviderResolver;

    private int order = Ordered.LOWEST_PRECEDENCE;

    @Override
    public Optional<MultifactorAuthenticationProvider> isActivated(final Authentication authentication,
                                                                   final RegisteredService registeredService,
                                                                   final HttpServletRequest httpServletRequest,
                                                                   final Service service) {

        val multifactorMap = casProperties.getAuthn().getAdaptive().getRequireMultifactor();

        if (service == null || authentication == null) {
            LOGGER.debug("No service or authentication is available to determine event for principal");
            return Optional.empty();
        }

        if (multifactorMap == null || multifactorMap.isEmpty()) {
            LOGGER.debug("Adaptive authentication is not configured to require multifactor authentication");
            return Optional.empty();
        }

        val providerMap = MultifactorAuthenticationUtils.getAvailableMultifactorAuthenticationProviders(ApplicationContextProvider.getApplicationContext());
        if (providerMap.isEmpty()) {
            LOGGER.error("No multifactor authentication providers are available in the application context");
            throw new AuthenticationException();
        }

        val clientInfo = ClientInfoHolder.getClientInfo();
        val clientIp = clientInfo.getClientIpAddress();
        LOGGER.debug("Located client IP address as [{}]", clientIp);

        val agent = WebUtils.getHttpServletRequestUserAgentFromRequestContext(httpServletRequest);

        val entries = multifactorMap.entrySet();
        for (final Map.Entry entry : entries) {
            val mfaMethod = entry.getKey().toString();
            val pattern = entry.getValue().toString();

            val providerFound = multifactorAuthenticationProviderResolver.resolveProvider(providerMap, mfaMethod);

            if (providerFound.isEmpty()) {
                LOGGER.error("Adaptive authentication is configured to require [{}] for [{}], yet [{}] is absent in the configuration.",
                    mfaMethod, pattern, mfaMethod);
                throw new AuthenticationException();
            }

            if (checkUserAgentOrClientIp(clientIp, agent, mfaMethod, pattern)) {
                return providerFound;
            }

            if (checkRequestGeoLocation(httpServletRequest, clientIp, mfaMethod, pattern)) {
                return providerFound;
            }
        }
        return Optional.empty();
    }

    private static boolean checkUserAgentOrClientIp(final String clientIp, final String agent, final String mfaMethod, final String pattern) {
        if (agent.matches(pattern) || clientIp.matches(pattern)) {
            LOGGER.debug("Current user agent [{}] at [{}] matches the provided pattern [{}] for "
                    + "adaptive authentication and is required to use [{}]",
                agent, clientIp, pattern, mfaMethod);
            return true;
        }
        return false;
    }

    private boolean checkRequestGeoLocation(final HttpServletRequest httpServletRequest, final String clientIp, final String mfaMethod, final String pattern) {
        if (this.geoLocationService != null) {
            val location = WebUtils.getHttpServletRequestGeoLocation(httpServletRequest);
            val loc = this.geoLocationService.locate(clientIp, location);
            if (loc != null) {
                val address = loc.build();
                if (address.matches(pattern)) {
                    LOGGER.debug("Current address [{}] at [{}] matches the provided pattern [{}] for "
                            + "adaptive authentication and is required to use [{}]",
                        address, clientIp, pattern, mfaMethod);
                    return true;
                }
            }
        }
        return false;
    }
}
