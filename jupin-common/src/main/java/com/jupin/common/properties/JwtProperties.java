package com.jupin.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jupin.jwt")
@Data
public class JwtProperties {
    private String secret;
    private long expiration;
}
