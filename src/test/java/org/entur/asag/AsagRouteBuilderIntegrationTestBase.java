package org.entur.asag;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

@ActiveProfiles("test")
@RunWith(CamelSpringRunner.class)
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AsagRouteBuilderIntegrationTestBase {

    @Autowired
    protected ModelCamelContext context;

    @Before
    public void setUp() throws IOException {
    }

    protected void replaceEndpoint(String routeId,String originalEndpoint,String replacementEndpoint) throws Exception {
        context.getRouteDefinition(routeId).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(originalEndpoint)
                        .skipSendToOriginalEndpoint().to(replacementEndpoint);
            }
        });
    }
}
