package br.com.deladopara.eventing.infrastructure;

import br.com.deladopara.eventing.application.EventReplayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs administrator-requested replays on the worker; enabled with {@code eventing.replay.enabled=true}. */
@Component
@Profile("worker")
@EnableScheduling
@ConditionalOnProperty(prefix = "eventing.replay", name = "enabled", havingValue = "true")
public class EventReplayWorker {

    private final EventReplayService replays;
    private final int batchSize;

    public EventReplayWorker(EventReplayService replays, @Value("${eventing.replay.batch-size:10}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Event replay batch size must be positive");
        }
        this.replays = replays;
        this.batchSize = batchSize;
    }

    /** Returns how many requests this tick closed. */
    @Scheduled(fixedDelayString = "${eventing.replay.poll-delay:PT5S}")
    public int tick() {
        var done = 0;
        while (done < batchSize && replays.replayNext()) {
            done++;
        }
        return done;
    }
}
