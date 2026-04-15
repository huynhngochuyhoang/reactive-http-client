package io.github.huynhngochuyhoang.httpstarter.enable;

import io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientsRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enables scanning and auto-registration of {@code @ReactiveHttpClient} interfaces.
 * <p>
 * Place on any {@code @Configuration} or {@code @SpringBootApplication} class:
 * <pre>{@code
 * @SpringBootApplication
 * @EnableReactiveHttpClients(basePackages = "com.myapp.client")
 * public class App { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ReactiveHttpClientsRegistrar.class)
public @interface EnableReactiveHttpClients {

    /** Base packages to scan for {@code @ReactiveHttpClient} interfaces. */
    String[] basePackages() default {};

    /** Type-safe alternative to {@link #basePackages()}. */
    Class<?>[] basePackageClasses() default {};
}
