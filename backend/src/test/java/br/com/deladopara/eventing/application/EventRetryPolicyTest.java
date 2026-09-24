package br.com.deladopara.eventing.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.eventing.application.EventRetryPolicy.FailureKind;
import java.time.Duration;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class EventRetryPolicyTest {

    private final EventRetryPolicy policy = new EventRetryPolicy(RandomGenerator.of("L64X128MixRandom"));

    @Test
    void ceilingDoublesFromOneSecondAndCapsAtOneMinute() {
        assertThat(policy.ceiling(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.ceiling(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.ceiling(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(policy.ceiling(7)).isEqualTo(Duration.ofSeconds(60));
        assertThat(policy.ceiling(500)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void fullJitterStaysWithinZeroAndCeilingAndVaries() {
        var delays = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> policy.delay(4))
                .toList();

        assertThat(delays).allSatisfy(delay -> assertThat(delay).isBetween(Duration.ZERO, Duration.ofSeconds(8)));
        assertThat(delays.stream().distinct().count()).isGreaterThan(20);
    }

    @Test
    void transientFailuresGetEightAttemptsAndInvalidOnlyOne() {
        assertThat(policy.exhausted(FailureKind.TRANSIENT, 7)).isFalse();
        assertThat(policy.exhausted(FailureKind.TRANSIENT, 8)).isTrue();
        assertThat(policy.exhausted(FailureKind.INVALID, 1)).isTrue();
    }

    @Test
    void classifiesInvalidAndUnsupportedAsInvalidAndEverythingElseAsTransient() {
        assertThat(EventRetryPolicy.classify(new InvalidEventEnvelopeException("bad")))
                .isEqualTo(FailureKind.INVALID);
        assertThat(EventRetryPolicy.classify(new UnsupportedEventException("t", 9)))
                .isEqualTo(FailureKind.INVALID);
        assertThat(EventRetryPolicy.classify(new IllegalStateException("db down")))
                .isEqualTo(FailureKind.TRANSIENT);
    }

    @Test
    void deterministicRandomProducesDeterministicDelay() {
        var seeded = new EventRetryPolicy(new SplittableRandom(7L));
        assertThat(seeded.delay(3)).isEqualTo(new EventRetryPolicy(new SplittableRandom(7L)).delay(3));
    }
}
