/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package org.entur.asag;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelSpringBootApplicationController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"org.entur.asag", "org.rutebanken.helper"})
public class AsagApp extends RouteBuilder {

    private static final Logger logger = LoggerFactory.getLogger(AsagApp.class);

    @Autowired
    ProducerTemplate producerTemplate;

    public static void main(String... args) throws Exception {
        logger.info("Starting Asag ...");

        ConfigurableApplicationContext applicationContext = new SpringApplicationBuilder(AsagApp.class)
                .web(WebApplicationType.NONE)
                .run(args);

        CamelSpringBootApplicationController applicationController = applicationContext.getBean(CamelSpringBootApplicationController.class);

        AsagApp asagApp = applicationContext.getBean(AsagApp.class);
        asagApp.run();


    }

    private void run() {

        producerTemplate.sendBody("direct:uploadTiamatToMapboxAsGeoJson", "Startup payload");
        logger.info("Sent message to direct:uploadTiamatToMapboxAsGeoJson");
    }

    @Override
    public void configure() throws Exception {
    }
}
