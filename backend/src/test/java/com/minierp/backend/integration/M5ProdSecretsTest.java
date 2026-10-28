package com.minierp.backend.integration;
import com.minierp.backend.BackendApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class M5ProdSecretsTest {
    @ParameterizedTest
    @ValueSource(strings={"DB_APP_USERNAME","DB_APP_PASSWORD","DB_MIGRATOR_USERNAME","DB_MIGRATOR_PASSWORD","JWT_SECRET"})
    void missingSecretFailsBeforeOpeningDatabase(String missing) {
        Map<String,Object> properties=new HashMap<>();
        for(String key:List.of("DB_APP_USERNAME","DB_APP_PASSWORD","DB_MIGRATOR_USERNAME","DB_MIGRATOR_PASSWORD","JWT_SECRET"))properties.put(key,UUID.randomUUID().toString());
        properties.remove(missing);
        assertThatThrownBy(()->new SpringApplicationBuilder(BackendApplication.class)
            .web(WebApplicationType.NONE).profiles("prod").logStartupInfo(false)
            .properties("spring.config.location=classpath:application-prod.properties","logging.level.root=OFF","spring.main.banner-mode=off")
            .initializers(c->{
                c.getEnvironment().getPropertySources().remove("systemEnvironment");
                c.getEnvironment().getPropertySources().remove("systemProperties");
                c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("missing-production-secret",properties));
            }).run()).hasStackTraceContaining("Required production setting is missing: "+missing);
        System.out.println("M5_PROD missing="+missing+" startup=REJECTED before_external_connections");
    }
}
