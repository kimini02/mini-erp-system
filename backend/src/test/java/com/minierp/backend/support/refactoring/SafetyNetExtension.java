package com.minierp.backend.support.refactoring;

import org.junit.jupiter.api.extension.*;
/** Discovered for the whole suite; disabled unless refactoring.record points to an output directory. */
public final class SafetyNetExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback,
        AfterAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        if (!SafetyNetRun.enabled()) {
            return;
        }
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .getOrComputeIfAbsent(SafetyNetExtension.class, key -> new RunResource());
        SafetyNetRun.begin(context.getRequiredTestClass().getName() + "#<before-all>");
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (SafetyNetRun.enabled()) {
            String method = context.getRequiredTestMethod().getName() + "()";
            String unique = context.getUniqueId();
            int invocation = unique.indexOf("[test-template-invocation:");
            if (invocation >= 0) {
                method += unique.substring(invocation);
            }
            SafetyNetRun.begin(context.getRequiredTestClass().getName() + "#" + method);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (SafetyNetRun.enabled()) {
            SafetyNetRun.begin(context.getRequiredTestClass().getName() + "#<after-each>");
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (SafetyNetRun.enabled()) {
            SafetyNetRun.begin(context.getRequiredTestClass().getName() + "#<after-all>");
        }
    }

    private static final class RunResource implements ExtensionContext.Store.CloseableResource {
        private RunResource() {
            JdbcRecording.install();
        }
        @Override
        public void close() throws Exception {
            SafetyNetRun.write();
        }
    }
}
