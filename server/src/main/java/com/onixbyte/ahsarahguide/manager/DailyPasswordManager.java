package com.onixbyte.ahsarahguide.manager;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.onixbyte.ahsarahguide.domain.dto.DailyPasswordResponse;
import com.onixbyte.ahsarahguide.exeption.InternalServerErrorException;
import com.onixbyte.ahsarahguide.shared.JacksonModules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Manager for daily password data access and caching coordination.
 *
 * @author zihluwang
 */
@Component
public class DailyPasswordManager {

    private static final String CACHE_KEY_PREFIX = "daily-password:";

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public DailyPasswordManager(
            RestClient.Builder restClientBuilder,
            RedisTemplate<String, Object> redisTemplate
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
        this.redisTemplate = redisTemplate;
    }

    /**
     * Retrieves the daily password from cache or generates a new one.
     * @return the daily password response
     */
    public DailyPasswordResponse getDailyPassword() {
        var key = CACHE_KEY_PREFIX + LocalDate.now();

        // Attempt to fetch the cached response
        var cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return (DailyPasswordResponse) cached;
        }

        DailyPasswordResponse response;
        try {
            // Fetch from the upstream remote service
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to connect to the upstream service.", e);
        }

        if (Objects.isNull(response)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No daily password data available.");
        }

        // Calculate the precise duration until midnight to optimise Redis memory usage
        var now = LocalDateTime.now();
        var midnight = now.toLocalDate().atStartOfDay().plusDays(1);
        var durationUntilMidnight = Duration.between(now, midnight);

        // Save to Redis with the calculated TTL
        redisTemplate.opsForValue().set(key, response, durationUntilMidnight);

        return response;
    }
}
