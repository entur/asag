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

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan({"org.entur.asag", "org.rutebanken.helper"})
public class AsagApp implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AsagApp.class);

    @Produce("direct:uploadTiamatToMapboxAsGeoJson")
    private ProducerTemplate producerTemplate;

    @Value("${asag.run.on.startup:true}")
    private boolean runOnStartup;

    public static void main(String[] args) {
        logger.info("Starting Asag ...");
        SpringApplication app = new SpringApplication(AsagApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (runOnStartup) {
            producerTemplate.sendBody(null);
        }
    }
}
