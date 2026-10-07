package br.com.deladopara.payments.adapter.asaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.deladopara.payments.adapter.asaas.AsaasPaymentProvider.AsaasUnknownOutcomeException;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutRequest;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutStatus;
import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Recorded Asaas HTTP exchanges (docs.asaas.com, revalidated 2026-10-03); not sandbox evidence. */
class AsaasPaymentProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final String API_KEY = "$aact_test_key_never_logged";
    private static final UUID INTENT = UUID.fromString("00000000-0000-4000-8000-000000000060");
    private static final UUID ORDER = UUID.fromString("00000000-0000-4000-8000-000000000050");
    private static final String API = "https://api-sandbox.asaas.com";

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
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static CheckoutRequest request() {
        return new CheckoutRequest(INTENT, ORDER, 6_590, NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void createsAHostedPixOrCardCheckoutForTheExactSnapshotTotal() {
        server.expect(requestTo(API + "/v3/checkouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", API_KEY))
                .andExpect(jsonPath("$.billingTypes").value(org.hamcrest.Matchers.contains("PIX", "CREDIT_CARD")))
                .andExpect(jsonPath("$.chargeTypes[0]").value("DETACHED"))
                .andExpect(jsonPath("$.minutesToExpire").value(10))
                .andExpect(jsonPath("$.externalReference").value(INTENT.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].value").value(65.90))
                .andExpect(jsonPath("$.items[0].name").value(AsaasPaymentProvider.ITEM_NAME))
                .andExpect(jsonPath("$.items[0].imageBase64").isNotEmpty())
                .andExpect(jsonPath("$.callback.successUrl").value("https://loja.example.test/orders/" + ORDER))
                .andExpect(jsonPath("$.callback.cancelUrl").value("https://loja.example.test/orders/" + ORDER))
                .andRespond(withSuccess("""
                        {"id":"chk_123","link":"https://sandbox.asaas.com/checkoutSession/show?id=chk_123",
                         "status":"ACTIVE","minutesToExpire":10,"externalReference":"%s"}
                        """.formatted(INTENT), MediaType.APPLICATION_JSON));

        var created = provider.createCheckout(request());

        assertThat(created.checkoutId()).isEqualTo("chk_123");
        assertThat(created.url()).isEqualTo("https://sandbox.asaas.com/checkoutSession/show?id=chk_123");
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        server.verify();
    }

    @Test
    void clientErrorsAreRejectionsBeforeAnyEffect() {
        server.expect(requestTo(API + "/v3/checkouts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"invalid_value\",\"description\":\"Valor inválido\"}]}"));

        assertThatThrownBy(() -> provider.createCheckout(request()))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessage("HTTP_400");
    }

    @Test
    void invalidCredentialIsRejectedWithoutEchoingTheKey() {
        server.expect(requestTo(API + "/v3/checkouts")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.createCheckout(request()))
                .isInstanceOf(ProviderRejectedException.class)
                .hasMessage("HTTP_401")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void serverErrorAfterSendingIsUnknownNotARejection() {
        server.expect(requestTo(API + "/v3/checkouts")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> provider.createCheckout(request()))
                .isInstanceOf(AsaasUnknownOutcomeException.class)
                .isNotInstanceOf(ProviderRejectedException.class);
    }

    @Test
    void readTimeoutIsUnknownNotARejection() {
        server.expect(requestTo(API + "/v3/checkouts")).andRespond(withException(new SocketTimeoutException()));

        assertThatThrownBy(() -> provider.createCheckout(request()))
                .isInstanceOf(AsaasUnknownOutcomeException.class)
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    void conflictIsUnknownBecauseTheProviderMayHaveActed() {
        server.expect(requestTo(API + "/v3/checkouts")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> provider.createCheckout(request())).isInstanceOf(AsaasUnknownOutcomeException.class);
    }

    @Test
    void linkOutsideTheAllowedHostsIsNeverHandedToTheBuyer() {
        server.expect(requestTo(API + "/v3/checkouts"))
                .andRespond(withSuccess(
                        "{\"id\":\"chk_123\",\"link\":\"https://phishing.example/pay\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.createCheckout(request()))
                .isInstanceOf(AsaasUnknownOutcomeException.class)
                .hasMessageContaining("host");
    }

    @Test
    void plainHttpLinkIsRefused() {
        server.expect(requestTo(API + "/v3/checkouts"))
                .andRespond(withSuccess(
                        "{\"id\":\"chk_123\",\"link\":\"http://sandbox.asaas.com/c/chk_123\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.createCheckout(request())).isInstanceOf(AsaasUnknownOutcomeException.class);
    }

    @Test
    void responseWithoutIdIsUnknown() {
        server.expect(requestTo(API + "/v3/checkouts"))
                .andRespond(withSuccess("{\"link\":\"https://sandbox.asaas.com/c/x\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.createCheckout(request())).isInstanceOf(AsaasUnknownOutcomeException.class);
    }

    @Test
    void receivedPaymentReportsThePaidAmountAndItsCheckout() {
        server.expect(requestTo(API + "/v3/payments?externalReference=" + INTENT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("access_token", API_KEY))
                .andRespond(withSuccess("""
                        {"object":"list","hasMore":false,"totalCount":1,"data":[
                          {"id":"pay_1","status":"RECEIVED","value":65.9,"billingType":"PIX",
                           "checkoutSession":"chk_123","externalReference":"%s"}]}
                        """.formatted(INTENT), MediaType.APPLICATION_JSON));

        var state = provider.findCheckout(INTENT).orElseThrow();

        assertThat(state.checkoutId()).isEqualTo("chk_123");
        assertThat(state.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(state.amountCents()).isEqualTo(6_590);
    }

    @Test
    void confirmedCardPaymentCountsAsPaidEvenNextToAnOpenOne() {
        server.expect(requestTo(API + "/v3/payments?externalReference=" + INTENT))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"pay_1","status":"PENDING","value":65.9,"checkoutSession":"chk_123"},
                          {"id":"pay_2","status":"CONFIRMED","value":65.0,"checkoutSession":"chk_123"}]}
                        """, MediaType.APPLICATION_JSON));

        var state = provider.findCheckout(INTENT).orElseThrow();

        assertThat(state.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(state.amountCents()).isEqualTo(6_500);
    }

    @Test
    void openPaymentIsPending() {
        server.expect(requestTo(API + "/v3/payments?externalReference=" + INTENT))
                .andRespond(withSuccess(
                        "{\"data\":[{\"id\":\"pay_1\",\"status\":\"PENDING\",\"value\":65.9,"
                                + "\"checkoutSession\":\"chk_123\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.findCheckout(INTENT).orElseThrow().status()).isEqualTo(CheckoutStatus.PENDING);
    }

    @Test
    void noPaymentYetIsEmpty() {
        server.expect(requestTo(API + "/v3/payments?externalReference=" + INTENT))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(provider.findCheckout(INTENT)).isEmpty();
    }

    @Test
    void linkIsShortenedToEndBeforeTheStockHold() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var provider = new AsaasPaymentProvider(
                builder,
                new AsaasProperties(
                        URI.create(API),
                        API_KEY,
                        URI.create("https://loja.example.test"),
                        15,
                        Duration.ofSeconds(5),
                        Set.of("sandbox.asaas.com")),
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        server.expect(requestTo(API + "/v3/checkouts"))
                .andExpect(jsonPath("$.minutesToExpire").value(12))
                .andRespond(withSuccess("""
                        {"id":"chk_late","link":"https://sandbox.asaas.com/checkoutSession/show?id=chk_late"}
                        """, MediaType.APPLICATION_JSON));

        var created =
                provider.createCheckout(new CheckoutRequest(INTENT, ORDER, 6_590, NOW.plus(Duration.ofMinutes(13))));

        assertThat(created.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(12)));
        server.verify();
    }

    @Test
    void holdTooShortForAsaasIsRefusedWithoutCallingIt() {
        var late = new CheckoutRequest(INTENT, ORDER, 6_590, NOW.plus(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> provider.createCheckout(late))
                .isInstanceOfSatisfying(
                        ProviderRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo("RESERVATION_TOO_SHORT"));
        server.verify();
    }

    @Test
    void lookupFailureIsNeverReadAsNotPaid() {
        server.expect(requestTo(API + "/v3/payments?externalReference=" + INTENT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> provider.findCheckout(INTENT)).isInstanceOf(AsaasUnknownOutcomeException.class);
    }

    @Test
    void configurationRefusesLinksOutlivingTheReservationAndPlainHttp() {
        var api = URI.create(API);
        var store = URI.create("https://loja.example.test");
        var timeout = Duration.ofSeconds(5);
        var hosts = Set.of("sandbox.asaas.com");

        assertThatThrownBy(() -> new AsaasProperties(api, API_KEY, store, 16, timeout, hosts))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsaasProperties(api, API_KEY, store, 9, timeout, hosts))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsaasProperties(URI.create("http://api"), API_KEY, store, 10, timeout, hosts))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AsaasProperties(api, " ", store, 10, timeout, hosts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(API_KEY);
    }
}
