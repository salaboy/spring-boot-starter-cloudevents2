package com.salaboy.springbootcloudeventsstarter;

import io.cloudevents.CloudEvent;
import io.cloudevents.v03.AttributesImpl;
import io.cloudevents.v03.CloudEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Map;

@SpringBootApplication
@RestController
public class SpringBootCloudEventsStarterApplication {

	@Value("${host:localhost}")
	private String host;

	@Value("${fnhost:localhost}")
	private String fnHost;

	public static void main(String[] args) {
		SpringApplication.run(SpringBootCloudEventsStarterApplication.class, args);
	}

	@PostMapping
	public void recieveCloudEvent(@RequestHeader Map<String, String> headers, @RequestBody Object body) {

		CloudEvent<AttributesImpl, String> cloudEvent = readCloudEventFromRequest(headers, body);
		System.out.println("I got a cloud event: " + cloudEvent.toString());
		System.out.println(" -> cloud event attr: " + cloudEvent.getAttributes());
		System.out.println(" -> cloud event data: " + cloudEvent.getData());

	}

	private CloudEvent<AttributesImpl, String> readCloudEventFromRequest(Map<String, String> headers, Object body) {
		return CloudEventBuilder.<String>builder()

				.withId(headers.get("ce-id"))
				.withType(headers.get("ce-type"))
				.withSource((headers.get("ce-source")!=null)?URI.create(headers.get("ce-source")):null)
				.withData((body != null)?body.toString():"")
				.withDatacontenttype((headers.get("Content-Type") != null)?headers.get("Content-Type"):"application/json").build();
	}

	@GetMapping
	public CloudEvent sendCloudEvent() {
		final CloudEvent<AttributesImpl, String> myCloudEvent = CloudEventBuilder.<String>builder()

				.withId("1234-abcd")
				.withType("java-event")
				.withSource(URI.create("cloudevents-java.default.svc.cluster.local"))
				.withData("{\"name\" : \"Other From Java Cloud Event\" }")
				.withDatacontenttype("application/json")
				.build();
		WebClient webClient = WebClient.builder().baseUrl("http://" + host ).build();
		WebClient.RequestBodySpec uri = webClient.post().uri("/");
		WebClient.RequestHeadersSpec<?> headersSpec = uri.body(BodyInserters.fromValue(myCloudEvent.getData()));
		AttributesImpl attributes = myCloudEvent.getAttributes();
		WebClient.RequestHeadersSpec<?> header = headersSpec
				.header("ce-id", attributes.getId())
				.header("ce-specversion", attributes.getSpecversion())
				.header("Content-Type", (attributes.getDatacontenttype().isPresent())?attributes.getDatacontentencoding().get():"")
				.header("ce-type", attributes.getType())
				.header("ce-time", (attributes.getTime().isPresent())?attributes.getTime().get().toString():"")
				.header("ce-source", (attributes.getSource()!=null)?attributes.getSource().toString():"")
				.header("HOST", fnHost); //. this is the ksvc host
		WebClient.ResponseSpec responseSpec = header.retrieve();
		responseSpec.bodyToMono(String.class).doOnError(t -> t.printStackTrace())
				.doOnSuccess(s -> System.out.println("Result -> "+s)).subscribe();

		return myCloudEvent;
	}

}
