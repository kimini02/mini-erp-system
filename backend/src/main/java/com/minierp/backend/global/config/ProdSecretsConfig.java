package com.minierp.backend.global.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
/** Fail before creating any DB/Redis clients. Never include values in diagnostics. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProdSecretsConfig {
    @Bean
    static BeanFactoryPostProcessor requiredProductionSecrets(Environment environment) {
        return factory -> {
            for (String key : List.of("DB_APP_USERNAME", "DB_APP_PASSWORD", "DB_MIGRATOR_USERNAME",
                    "DB_MIGRATOR_PASSWORD", "JWT_SECRET")) {
                String value = environment.getProperty(key);
                if (value == null || value.isBlank() || value.equals("CHANGE_ME")) {
                    throw new IllegalStateException("Required production setting is missing: " + key);
                }
            }
        };
    }
}
