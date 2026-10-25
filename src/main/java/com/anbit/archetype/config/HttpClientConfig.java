package com.anbit.archetype.config;

import com.anbit.archetype.integration.WarehouseClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.service.registry.ImportHttpServices;

/** Registers outbound HTTP clients (one group per system) and enables {@code @Retryable}. */
@Configuration
@EnableResilientMethods
@ImportHttpServices(group = "warehouse", types = WarehouseClient.class)
public class HttpClientConfig {
}
