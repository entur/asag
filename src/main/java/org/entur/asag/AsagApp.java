package org.entur.asag;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsagApp extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(AsagApp.class);

    public static void main(String... args) {
        logger.info("Starting Asag ...");
        SpringApplication app = new SpringApplication(AsagApp.class);
        app.setWebEnvironment(false);
        app.run(args);
    }

    @Override
    public void configure() throws Exception {
    }
}
