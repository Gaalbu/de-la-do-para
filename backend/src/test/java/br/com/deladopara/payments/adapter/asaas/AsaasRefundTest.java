package br.com.deladopara.payments.adapter.asaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.deladopara.payments.adapter.asaas.AsaasPaymentProvider.AsaasUnknownOutcomeException;
import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import br.com.deladopara.payments.application.RefundProvider.RefundRequest;
import br.com.deladopara.payments.application.RefundProvider.RefundState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Recorded Asaas refund exchanges ({@code POST /v3/payments/{id}/refund}, docs.asaas.com); not sandbox evidence (C04).
 */
class AsaasRefundTest {

    private static final String API_KEY = "$aact_test_key_never_logged";
    private static final UUID INTENT = UUID.fromString("00000000-0000-4000-8000-000000000067");
    private static final String API = "https://api-sandbox.asaas.com";
    private static final String PAYMENTS = API + "/v3/payments?externalReference=" + INTENT;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final AsaasPaymentProvider provider = new AsaasPaymentProvider(
            builder,
            new AsaasProperties(
                    URI.create(API),
                    API_KEY,
                    URI.create("https://loja.example.test"),
                    10,
                    Duration.ofSeconds(5),
                    Set.of("sandbox.asaas.com")),
            new ObjectMapper(),
            Clock.systemUTC());

    private void paymentsAre(String data) {
        server.expect(requestTo(PAYMENTS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[" + data + "]}", MediaType.APPLICATION_JSON));
    }

    @Test
    void refundsThePaidPaymentInFullWithoutSendingAValue() {
        paymentsAre("""
                {"id":"pay_1","status":"PENDING","value":65.9},
                {"id":"pay_2","status":"RECEIVED","value":65.9}""");
        server.expect(requestTo(API + "/v3/payments/pay_2/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", API_KEY))
                .andExpect(content().json("{}", true))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_2\",\"status\":\"REFUNDED\",\"value\":65.9}", MediaType.APPLICATION_JSON));

        assertThat(provider.refund(new RefundRequest(INTENT, 6_590))).isEqualTo(RefundState.DONE);
        server.verify();
    }

    @Test
    void refundStillInProgressIsPending() {
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"CONFIRMED\",\"value\":65.9}");
        server.expect(requestTo(API + "/v3/payments/pay_2/refund"))
                .andRespond(withSuccess(
                        "{\"id\":\"pay_2\",\"status\":\"REFUND_IN_PROGRESS\"}", MediaType.APPLICATION_JSON));

        assertThat(provider.refund(new RefundRequest(INTENT, 6_590))).isEqualTo(RefundState.PENDING);
    }

    @Test
    void withoutAPaidPaymentNothingIsSent() {
        paymentsAre("{\"id\":\"pay_1\",\"status\":\"PENDING\",\"value\":65.9}");

        assertThatThrownBy(() -> provider.refund(new RefundRequest(INTENT, 6_590)))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessage("NO_PAID_PAYMENT");
        server.verify();
    }

    @Test
    void paidAmountDifferentFromTheIntentIsNotRefundedAutomatically() {
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"RECEIVED\",\"value\":60.0}");

        assertThatThrownBy(() -> provider.refund(new RefundRequest(INTENT, 6_590)))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessage("AMOUNT_MISMATCH");
        server.verify();
    }

    @Test
    void clientErrorOnTheRefundIsARejection() {
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"RECEIVED\",\"value\":65.9}");
        server.expect(requestTo(API + "/v3/payments/pay_2/refund"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"invalid_action\"}]}"));

        assertThatThrownBy(() -> provider.refund(new RefundRequest(INTENT, 6_590)))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessage("HTTP_400");
    }

    @Test
    void timeoutOnTheRefundIsUnknownNotARejection() {
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"RECEIVED\",\"value\":65.9}");
        server.expect(requestTo(API + "/v3/payments/pay_2/refund"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> provider.refund(new RefundRequest(INTENT, 6_590)))
                .isInstanceOf(AsaasUnknownOutcomeException.class);
    }

    @Test
    void lookupReportsSettledPendingOrNoRefund() {
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"REFUNDED\",\"value\":65.9}");
        assertThat(provider.findRefund(INTENT)).contains(RefundState.DONE);

        server.reset();
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"REFUND_REQUESTED\",\"value\":65.9}");
        assertThat(provider.findRefund(INTENT)).contains(RefundState.PENDING);

        server.reset();
        paymentsAre("{\"id\":\"pay_2\",\"status\":\"RECEIVED\",\"value\":65.9}");
        assertThat(provider.findRefund(INTENT)).isEmpty();
    }
}
