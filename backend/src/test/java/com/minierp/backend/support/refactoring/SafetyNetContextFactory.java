package com.minierp.backend.support.refactoring;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.*;
import java.util.List;
/** Adds a result handler to every Boot-managed MockMvc without editing individual tests. */
public final class SafetyNetContextFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass,
            List<ContextConfigurationAttributes> attributes) {
        return SafetyNetRun.enabled() ? Customizer.INSTANCE : null;
    }

    private enum Customizer implements ContextCustomizer {
        INSTANCE;
        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
            context.getBeanFactory()
                    .registerSingleton("refactoringResponseRecorder",
                            (MockMvcBuilderCustomizer) builder -> builder.addFilters(new RecordingOrder())
                                    .alwaysDo(result -> SafetyNetRun.response(result)));
        }
    }
}
