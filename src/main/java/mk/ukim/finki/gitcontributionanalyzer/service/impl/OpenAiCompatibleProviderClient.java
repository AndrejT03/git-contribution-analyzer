package mk.ukim.finki.gitcontributionanalyzer.service.impl;
import mk.ukim.finki.gitcontributionanalyzer.config.AppSettings;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiFailureReason;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiProvider;
import mk.ukim.finki.gitcontributionanalyzer.exception.AiProviderException;
import mk.ukim.finki.gitcontributionanalyzer.model.AiProviderSelection;
import mk.ukim.finki.gitcontributionanalyzer.service.AiProviderClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class OpenAiCompatibleProviderClient implements AiProviderClient {

    public static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiCompatibleProviderClient(AppSettings settings, ObjectMapper objectMapper) {
        this(createRestClient(settings), objectMapper);
    }

    public OpenAiCompatibleProviderClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Completion complete(
            AiProviderSelection providerSelection,
            String apiKey,
            String prompt) {
        AiProvider provider = providerSelection.provider();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException(AiFailureReason.MISSING_API_KEY, provider);
        }
        if (providerSelection.model().isBlank()) {
            throw new AiProviderException(AiFailureReason.MISSING_MODEL, provider);
        }

        try {
            byte[] responseBody = restClient.post()
                    .uri(provider.chatCompletionsEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(createRequest(providerSelection.model(), prompt))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            throw new AiProviderException(mapHttpFailure(status), provider);
                        }
                        return readBounded(response.getBody(), provider);
                    });

            return parseCompletion(responseBody, provider);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw mapRestClientFailure(exception, provider);
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException(AiFailureReason.REQUEST_REJECTED, provider, exception);
        }
    }

    private Map<String, Object> createRequest(String model, String prompt) {
        return Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt
                )),
                "stream", false
        );
    }

    private Completion parseCompletion(byte[] responseBody, AiProvider provider) {
        if (responseBody == null || responseBody.length == 0) {
            return new Completion("", "");
        }

        try {
            JsonNode response = objectMapper.readTree(responseBody);
            if (response == null || response.isNull()) {
                return new Completion("", "");
            }
            JsonNode firstChoice = response.path("choices").path(0);
            JsonNode message = firstChoice.path("message");
            String content = textContent(message.path("content"));
            String finishReason = firstChoice.path("finish_reason").asString("");
            if (!message.path("refusal").asString("").isBlank()) {
                finishReason = "content_filter";
            }
            return new Completion(content, finishReason);
        } catch (JacksonException exception) {
            throw new AiProviderException(AiFailureReason.INVALID_RESPONSE, provider, exception);
        }
    }

    private String textContent(JsonNode contentNode) {
        if (contentNode.isTextual()) {
            return contentNode.asString("");
        }
        if (!contentNode.isArray()) {
            return "";
        }

        StringBuilder content = new StringBuilder();
        for (JsonNode part : contentNode) {
            if (part.isTextual()) {
                content.append(part.asString(""));
                continue;
            }
            String text = part.path("text").asString("");
            if (!text.isBlank()) {
                content.append(text);
            }
        }
        return content.toString();
    }

    private static byte[] readBounded(InputStream input, AiProvider provider) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        byte[] body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new AiProviderException(AiFailureReason.INVALID_RESPONSE, provider);
        }
        return body;
    }

    public AiProviderException mapRestClientFailure(
            RestClientException exception,
            AiProvider provider) {
        if (exception instanceof ResourceAccessException) {
            AiFailureReason reason = hasTimeoutCause(exception)
                    ? AiFailureReason.TIMEOUT
                    : AiFailureReason.NETWORK_ERROR;
            return new AiProviderException(reason, provider, exception);
        }
        return new AiProviderException(AiFailureReason.SERVICE_UNAVAILABLE, provider, exception);
    }

    public AiFailureReason mapHttpFailure(int status) {
        return switch (status) {
            case 401, 403 -> AiFailureReason.CREDENTIALS_REJECTED;
            case 404 -> AiFailureReason.MODEL_UNAVAILABLE;
            case 402, 429 -> AiFailureReason.RATE_LIMITED;
            case 408, 504 -> AiFailureReason.TIMEOUT;
            default -> status >= 500 && status <= 599
                    ? AiFailureReason.SERVICE_UNAVAILABLE
                    : AiFailureReason.REQUEST_REJECTED;
        };
    }

    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                return false;
            }
            current = cause;
        }
        return false;
    }

    private static RestClient createRestClient(AppSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(settings.aiTimeoutSeconds()));

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}