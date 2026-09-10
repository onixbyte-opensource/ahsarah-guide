package com.onixbyte.ahsarahguide.manager;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.onixbyte.ahsarahguide.domain.dto.DailyPasswordResponse;
import com.onixbyte.ahsarahguide.shared.JacksonModules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * Manager for daily password data access and caching coordination.
 *
 * @author zihluwang
 */
@Component
public class DailyPasswordManager {

    private static final String CACHE_NAME = "daily-password";

    private final RestClient restClient;

    @Autowired
    public DailyPasswordManager(
            RestClient.Builder restClientBuilder
    ) {
        var snakeCaseMapper = new ObjectMapper();
        snakeCaseMapper.setPropertyNamingStrategy(
                PropertyNamingStrategies.SnakeCaseStrategy.INSTANCE);
        snakeCaseMapper.configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        snakeCaseMapper.registerModule(JacksonModules.DATE_TIME_MODULE);

        this.restClient = restClientBuilder
                .baseUrl("https://tmini.net/api")
                .messageConverters(converters -> {
                    converters.removeIf(
                            MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(
                            new MappingJackson2HttpMessageConverter(snakeCaseMapper));
                })
                .build();
    }

    /**
     * Retrieves the daily password from cache or generates a new one.
     * @return the daily password response
     */
    @Cacheable(
            cacheNames = CACHE_NAME,
            key = "T(java.time.LocalDate).now().toString()",
            sync = true,
            unless = "#result == null",
            cacheManager = "longTermCacheManager"
    )
    public DailyPasswordResponse getDailyPassword() {
        DailyPasswordResponse response;
        try {
            response = restClient.get()
                    .uri((uriBuilder) -> uriBuilder
                            .path("/sjzmm")
                            .queryParam("ckey", "")
                            .queryParam("type", "json")
                            .build())
                    .retrieve()
                    .body(DailyPasswordResponse.class);
        } catch (Exception e) {
            // Catch network errors, timeouts, or 4xx/5xx responses from the upstream service
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法获取每日密码数据。", e);
        }

        if (Objects.isNull(response)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法获取每日密码数据。");
        }

        return response;
    }
}
