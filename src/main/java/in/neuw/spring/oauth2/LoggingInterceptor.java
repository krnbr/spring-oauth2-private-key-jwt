package in.neuw.spring.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    private final String flowIdentifier;
    
    public LoggingInterceptor(final String flowIdentifier) {
        this.flowIdentifier = flowIdentifier;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        return logResponse(request, response);
    }

    private void logRequest(HttpRequest request, byte[] body) {
        log.info("Rest Client Call - {} - Request: {} {}", flowIdentifier, request.getMethod(), request.getURI());
        logHeaders(request.getHeaders());
        if (body.length > 0) {
            log.info("Rest Client Call - {} - Request body: {}", flowIdentifier, new String(body, StandardCharsets.UTF_8));
        }
    }

    private ClientHttpResponse logResponse(HttpRequest request, ClientHttpResponse response) throws IOException {
        log.info("Rest Client Call - {} - Response status: {}", flowIdentifier, response.getStatusCode());
        logHeaders(response.getHeaders());

        byte[] responseBody = response.getBody().readAllBytes();
        if (responseBody.length > 0) {
            log.info("Rest Client Call - {} - Response body: {}", flowIdentifier, new String(responseBody, StandardCharsets.UTF_8));
        }

        return new BufferingClientHttpResponseWrapper(response, responseBody);
    }

    private void logHeaders(HttpHeaders headers) {
        headers.forEach((name, values) -> values.forEach(value -> log.debug("{}={}", name, value)));
    }

    private record BufferingClientHttpResponseWrapper(ClientHttpResponse response,
                                                      byte[] body) implements ClientHttpResponse {

        @Override
        public InputStream getBody() throws IOException {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public void close() {
            response.close();
        }
    }

}