package mk.ukim.finki.gitcontributionanalyzer.service;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiFailureReason;
import mk.ukim.finki.gitcontributionanalyzer.enums.AiProvider;
import mk.ukim.finki.gitcontributionanalyzer.exception.AiProviderException;
import mk.ukim.finki.gitcontributionanalyzer.model.AiProviderSelection;
import mk.ukim.finki.gitcontributionanalyzer.service.impl.OpenAiCompatibleProviderClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleProviderClientTest {

    @ParameterizedTest
    @EnumSource(AiProvider.class)
    void callsTheFixedCompatibilityEndpointForEverySupportedProvider(AiProvider provider) {
        ClientFixture fixture = fixture();
        String model = provider.supportedModels().iterator().next();
        AiProviderSelection selection = new AiProviderSelection(provider, model);
        fixture.server().expect(requestTo(provider.chatCompletionsEndpoint()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer request-scoped-secret"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.model").value(model))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("analysis prompt"))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"analysis-json"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        AiProviderClient.Completion completion = fixture.client().complete(
                selection,
                "request-scoped-secret",
                "analysis prompt"
        );

        assertThat(completion.content()).isEqualTo("analysis-json");
        assertThat(completion.finishReason()).isEqualTo("stop");
        fixture.server().verify();
    }

    @Test
    void combinesTextPartsReturnedByCompatibilityEndpoints() {
        ClientFixture fixture = fixture();
        fixture.server().expect(requestTo(AiProvider.ANTHROPIC.chatCompletionsEndpoint()))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {"content": [
                              {"type": "text", "text": "first"},
                              {"type": "text", "text": " second"}
                            ]},
                            "finish_reason": "stop"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiProviderClient.Completion completion = fixture.client().complete(
                AiProviderSelection.parse("anthropic::claude-sonnet-5"),
                "request-scoped-secret",
                "analysis prompt"
        );

        assertThat(completion.content()).isEqualTo("first second");
        fixture.server().verify();
    }

    @Test
    void normalizesAnExplicitProviderRefusalToABlockedFinishReason() {
        ClientFixture fixture = fixture();
        fixture.server().expect(requestTo(AiProvider.OPENAI.chatCompletionsEndpoint()))
                .andRespond(withSuccess("""
                        {
                          "choices": [{
                            "message": {"content": "partial output", "refusal": "provider-only detail"},
                            "finish_reason": "stop"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        AiProviderClient.Completion completion = fixture.client().complete(
                AiProviderSelection.parse("openai::gpt-5.6-terra"),
                "request-scoped-secret",
                "analysis prompt"
        );

        assertThat(completion.content()).isEqualTo("partial output");
        assertThat(completion.finishReason()).isEqualTo("content_filter");
        assertThat(completion.toString()).doesNotContain("provider-only detail");
        fixture.server().verify();
    }

    @Test
    void returnsAnEmptyCompletionForAnEmptySuccessfulResponse() {
        ClientFixture fixture = fixture();
        fixture.server().expect(requestTo(AiProvider.OPENAI.chatCompletionsEndpoint()))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        AiProviderClient.Completion completion = fixture.client().complete(
                AiProviderSelection.parse("openai::gpt-5.6-terra"),
                "request-scoped-secret",
                "analysis prompt"
        );

        assertThat(completion.content()).isEmpty();
        assertThat(completion.finishReason()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void returnsAnEmptyCompletionForAJsonNullResponse() {
        ClientFixture fixture = fixture();
        fixture.server().expect(requestTo(AiProvider.OPENAI.chatCompletionsEndpoint()))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        AiProviderClient.Completion completion = fixture.client().complete(
                AiProviderSelection.parse("openai::gpt-5.6-terra"),
                "request-scoped-secret",
                "analysis prompt"
        );

        assertThat(completion.content()).isEmpty();
        assertThat(completion.finishReason()).isEmpty();
        fixture.server().verify();
    }

    @Test
    void rejectsAProviderResponseLargerThanTheConfiguredSafetyBound() {
        ClientFixture fixture = fixture();
        String oversizedBody = "x".repeat(OpenAiCompatibleProviderClient.MAX_RESPONSE_BYTES + 1);
        fixture.server().expect(requestTo(AiProvider.OPENAI.chatCompletionsEndpoint()))
                .andRespond(withSuccess(oversizedBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.client().complete(
                AiProviderSelection.parse("openai::gpt-5.6-terra"),
                "request-scoped-secret",
                "analysis prompt"
        ))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> assertThat(((AiProviderException) exception).reason())
                        .isEqualTo(AiFailureReason.INVALID_RESPONSE));

        fixture.server().verify();
    }

    @ParameterizedTest
    @CsvSource({
            "400, REQUEST_REJECTED",
            "401, CREDENTIALS_REJECTED",
            "403, CREDENTIALS_REJECTED",
            "404, MODEL_UNAVAILABLE",
            "408, TIMEOUT",
            "429, RATE_LIMITED",
            "500, SERVICE_UNAVAILABLE",
            "504, TIMEOUT"
    })
    void mapsCompatibilityApiStatusesToStableReasons(int status, AiFailureReason expected) {
        OpenAiCompatibleProviderClient client = fixture().client();

        assertThat(client.mapHttpFailure(status)).isEqualTo(expected);
    }

    @Test
    void discardsProviderErrorBodiesAndReturnsAControlledFailure() {
        ClientFixture fixture = fixture();
        fixture.server().expect(requestTo(AiProvider.OPENAI.chatCompletionsEndpoint()))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"private-provider-detail\"}"));

        assertThatThrownBy(() -> fixture.client().complete(
                AiProviderSelection.parse("openai::gpt-5.6-terra"),
                "request-scoped-secret",
                "analysis prompt"
        ))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> {
                    AiProviderException providerException = (AiProviderException) exception;
                    assertThat(providerException.reason())
                            .isEqualTo(AiFailureReason.CREDENTIALS_REJECTED);
                    assertThat(providerException.getMessage())
                            .doesNotContain("private-provider-detail", "request-scoped-secret");
                });

        fixture.server().verify();
    }

    @Test
    void mapsTransportFailuresWithoutExposingTheirDetails() {
        OpenAiCompatibleProviderClient client = fixture().client();

        AiProviderException timeout = client.mapRestClientFailure(
                new ResourceAccessException(
                        "socket-details-that-must-stay-private",
                        new SocketTimeoutException("provider-details")
                ),
                AiProvider.OPENAI
        );
        AiProviderException network = client.mapRestClientFailure(
                new ResourceAccessException("dns-details-that-must-stay-private"),
                AiProvider.OPENAI
        );

        assertThat(timeout.reason()).isEqualTo(AiFailureReason.TIMEOUT);
        assertThat(network.reason()).isEqualTo(AiFailureReason.NETWORK_ERROR);
        assertThat(timeout.getMessage()).doesNotContain("socket-details", "provider-details");
        assertThat(network.getMessage()).doesNotContain("dns-details");
    }

    @Test
    void rejectsABlankApiKeyBeforeMakingARequest() {
        ClientFixture fixture = fixture();

        assertThatThrownBy(() -> fixture.client().complete(
                AiProviderSelection.parse("openai::gpt-5.6-terra"),
                " ",
                "analysis prompt"
        ))
                .isInstanceOf(AiProviderException.class)
                .satisfies(exception -> assertThat(((AiProviderException) exception).reason())
                        .isEqualTo(AiFailureReason.MISSING_API_KEY));

        fixture.server().verify();
    }

    private ClientFixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiCompatibleProviderClient client = new OpenAiCompatibleProviderClient(
                builder.build(),
                new ObjectMapper()
        );
        return new ClientFixture(client, server);
    }

    private record ClientFixture(
            OpenAiCompatibleProviderClient client,
            MockRestServiceServer server
    ) {
    }
}