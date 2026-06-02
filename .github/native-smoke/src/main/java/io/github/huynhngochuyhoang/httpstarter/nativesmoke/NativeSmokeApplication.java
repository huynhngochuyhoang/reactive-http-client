package io.github.huynhngochuyhoang.httpstarter.nativesmoke;

import io.github.huynhngochuyhoang.httpstarter.enable.EnableReactiveHttpClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableReactiveHttpClients(basePackageClasses = NativeSmokeClient.class)
public class NativeSmokeApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NativeSmokeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            context.getBean(NativeSmokeClient.class);
        }
    }
}
