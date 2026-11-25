package com.teamc.isow.backend.common;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * テストの引数に {@link AppLog} を渡す（テストごとに、このアプリのロガーが書いたログだけを集める）。
 * {@code @ExtendWith(AppLogExtension.class)} で使う
 */
public class AppLogExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(AppLogExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        AppLog log = new AppLog();
        log.start();
        context.getStore(NAMESPACE).put(AppLog.class, log);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        AppLog log = context.getStore(NAMESPACE).remove(AppLog.class, AppLog.class);
        if (log != null) {
            log.stop();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == AppLog.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(AppLog.class, AppLog.class);
    }
}
