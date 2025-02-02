package org.apereo.cas.support.saml.metadata.resolver;

import org.apereo.cas.configuration.model.support.saml.idp.SamlIdPProperties;
import org.apereo.cas.redis.core.util.RedisUtils;
import org.apereo.cas.support.saml.OpenSamlConfigBean;
import org.apereo.cas.support.saml.services.SamlRegisteredService;
import org.apereo.cas.support.saml.services.idp.metadata.SamlMetadataDocument;
import org.apereo.cas.support.saml.services.idp.metadata.cache.resolver.BaseSamlRegisteredServiceMetadataResolver;

import lombok.val;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This is {@link RedisSamlRegisteredServiceMetadataResolver}.
 *
 * @author Misagh Moayyed
 * @since 6.4.0
 */
public class RedisSamlRegisteredServiceMetadataResolver extends BaseSamlRegisteredServiceMetadataResolver {
    /**
     * Redis key prefix.
     */
    public static final String CAS_PREFIX = SamlMetadataDocument.class.getSimpleName() + ':';

    private final transient RedisTemplate<String, SamlMetadataDocument> redisTemplate;

    private final long scanCount;

    public RedisSamlRegisteredServiceMetadataResolver(final SamlIdPProperties samlIdPProperties,
                                                      final OpenSamlConfigBean configBean,
                                                      final RedisTemplate<String, SamlMetadataDocument> redisTemplate,
                                                      final long scanCount) {
        super(samlIdPProperties, configBean);
        this.redisTemplate = redisTemplate;
        this.scanCount = scanCount;
    }

    private static String getPatternRedisKey() {
        return CAS_PREFIX + '*';
    }

    @Override
    public Collection<? extends MetadataResolver> resolve(final SamlRegisteredService service, final CriteriaSet criteriaSet) {
        return RedisUtils.keys(redisTemplate, getPatternRedisKey(), this.scanCount)
            .map(redisKey -> redisTemplate.boundValueOps(redisKey).get())
            .filter(Objects::nonNull)
            .map(doc -> buildMetadataResolverFrom(service, doc))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    @Override
    public boolean supports(final SamlRegisteredService service) {
        val metadataLocation = service != null ? service.getMetadataLocation() : StringUtils.EMPTY;
        return metadataLocation.trim().startsWith("redis://");
    }

    @Override
    public void saveOrUpdate(final SamlMetadataDocument document) {
        val redisKey = CAS_PREFIX + document.getName() + ':' + document.getId();
        redisTemplate.boundValueOps(redisKey).set(document);
    }

    @Override
    public boolean isAvailable(final SamlRegisteredService service) {
        return supports(service);
    }
}
