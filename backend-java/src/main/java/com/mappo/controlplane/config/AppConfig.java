package com.mappo.controlplane.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MappoProperties.class)
public class AppConfig {
}
