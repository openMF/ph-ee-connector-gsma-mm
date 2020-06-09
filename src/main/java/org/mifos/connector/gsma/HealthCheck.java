package org.mifos.connector.gsma;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.mifos.connector.gsma.zeebe.ZeebeProcessStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HealthCheck extends RouteBuilder {

    @Autowired
    public ZeebeProcessStarter zeebeProcessStarter;

    @Override
    public void configure() {
        from("rest:GET:/")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(constant("All Good"));

        from("rest:GET:/accesstoken")
                .to("direct:get-access-token")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setBody(simple("${body}"));

        from("rest:GET:/deploy")
                .to("direct:test-bpmn")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        from("direct:test-bpmn")
                .id("test-bpmn")
                .process(exchange -> {
                    zeebeProcessStarter.startZeebeWorkflow("sampleProcess");
                });
    }
}
