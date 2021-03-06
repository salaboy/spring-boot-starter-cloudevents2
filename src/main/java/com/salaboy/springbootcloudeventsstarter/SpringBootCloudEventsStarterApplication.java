package com.salaboy.springbootcloudeventsstarter;

import io.cloudevents.CloudEvent;
import io.cloudevents.v03.AttributesImpl;
import io.cloudevents.v03.CloudEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@SpringBootApplication
@RestController

public class SpringBootCloudEventsStarterApplication {

	@Value("${host:localhost}")
	private String host;

	@Value("${fnhost:localhost}")
	private String fnHost;

	private final static Logger logger = Logger.getLogger(SpringBootCloudEventsStarterApplication.class.getName());

	public static void main(String[] args) {
		SpringApplication.run(SpringBootCloudEventsStarterApplication.class, args);
	}

	@PostMapping
	public void recieveCloudEvent(@RequestHeader Map<String, String> headers, @RequestBody Object body) {
		headers.forEach((key, value) -> System.out.println(key + ":" + value));
		CloudEvent<AttributesImpl, String> cloudEvent = readCloudEventFromRequest(headers, body);
		System.out.println("I got a cloud event: " + cloudEvent.toString());
		System.out.println(" -> cloud event attr: " + cloudEvent.getAttributes());
		System.out.println(" -> cloud event data: " + cloudEvent.getData());

		sendCloudEvent();

	}

	private CloudEvent<AttributesImpl, String> readCloudEventFromRequest(Map<String, String> headers, Object body) {
		return CloudEventBuilder.<String>builder()

				.withId(headers.get("Ce-Id"))
				.withType(headers.get("Ce-Type"))
				.withSource((headers.get("Ce-Source") != null) ? URI.create(headers.get("Ce-Source")) : null)
				.withData((body != null) ? body.toString() : "")
				.withDatacontenttype((headers.get("Content-Type") != null) ? headers.get("Content-Type") : "application/json")
				.build();
	}

	public CloudEvent sendCloudEvent() {
		final CloudEvent<AttributesImpl, String> myCloudEvent = CloudEventBuilder.<String>builder()

				.withId("1234-abcd")
				.withType("java-event")
				.withSource(URI.create("cloudevents-spring-boot-2.default.svc.cluster.local"))
				.withData("{\"name\" : \"Other From Java Cloud Event "+ UUID.randomUUID().toString() +"\" }")
				.withDatacontenttype("application/json")
				.build();
		WebClient webClient = WebClient.builder().baseUrl(host).filter(logRequest()).build();

		WebClient.RequestBodySpec uri = webClient.post().uri("");
		WebClient.RequestHeadersSpec<?> headersSpec = uri.body(BodyInserters.fromValue(myCloudEvent.getData()));
		AttributesImpl attributes = myCloudEvent.getAttributes();
		WebClient.RequestHeadersSpec<?> header = headersSpec
				.header("Ce-Id", attributes.getId())
				.header("Ce-Specversion", attributes.getSpecversion())
				.header("Content-Type", "application/json")
				.header("Ce-Type", attributes.getType())
				.header("Ce-Source", (attributes.getSource() != null) ? attributes.getSource().toString() : "")
				.header("HOST", fnHost); //. this is the ksvc host
		WebClient.ResponseSpec responseSpec = header.retrieve();

		responseSpec.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
				.doOnSuccess(s -> System.out.println("Result -> " + s)).subscribe();

		return myCloudEvent;
	}
	private static ExchangeFilterFunction logRequest() {
		return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
			logger.info("Request: " + clientRequest.method() + " - " + clientRequest.url());
			clientRequest.headers().forEach((name, values) -> values.forEach(value -> logger.info(name+"="+value)));
			return Mono.just(clientRequest);
		});
	}
}
