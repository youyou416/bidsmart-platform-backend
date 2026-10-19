package com.bidsmart.platform.captcha;

import com.bidsmart.platform.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class CaptchaServiceTest {
    private final Instant now = Instant.parse("2026-10-19T00:00:00Z");
    private final Map<String, CaptchaRecord> rows = new ConcurrentHashMap<>();
    private final AtomicReference<String> code = new AtomicReference<>();
    private CaptchaService service;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    @BeforeEach void setUp() {
        CaptchaRepository repository = new CaptchaRepository() {
            public void replace(CaptchaRecord record) {
                rows.values().removeIf(row -> row.getBinding().equals(record.getBinding())); rows.put(record.getId(), record);
            }
            public Optional<CaptchaRecord> consume(String id, String binding) {
                CaptchaRecord row = rows.get(id);
                return row != null && row.getBinding().equals(binding) && rows.remove(id, row) ? Optional.of(row) : Optional.empty();
            }
            public void deleteExpired(Instant cutoff) { rows.values().removeIf(row -> !row.getExpiresAt().isAfter(cutoff)); }
        };
        CaptchaImageRenderer renderer = new CaptchaImageRenderer() {
            @Override public String render(String answer) { code.set(answer); return super.render(answer); }
        };
        service = new CaptchaService(repository, renderer, new CaptchaProperties(), Clock.fixed(now, ZoneOffset.UTC));
        request.setRemoteAddr("127.0.0.1");
    }
    @Test void pngIsDecodableAndDatabaseContainsOnlyKeyedDigests() throws Exception {
        CaptchaChallenge challenge = service.issue(request);
        assertThat(code.get()).matches("[0-9]{4}");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(challenge.getImageDataUrl().split(",")[1])));
        assertThat(image.getWidth()).isEqualTo(120); assertThat(image.getHeight()).isEqualTo(40);
        CaptchaRecord row = rows.get(challenge.getCaptchaId());
        assertThat(row.getAnswerHash()).matches("[0-9a-f]{64}").isNotEqualTo(code.get());
        assertThat(row.getBinding()).matches("[0-9a-f]{64}").isNotEqualTo(request.getSession().getId());
        assertThat(row.getExpiresAt()).isEqualTo(now.plusSeconds(120));
        service.verify(challenge.getCaptchaId(), code.get(), request);
        assertThat(rows).isEmpty();
    }
    @Test void simultaneousAnswersHaveExactlyOneWinner() throws Exception {
        CaptchaChallenge challenge = service.issue(request);
        String answer = code.get();
        java.util.concurrent.ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = pool.invokeAll(java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<Boolean>>mapToObj(i -> () -> {
                        try { service.verify(challenge.getCaptchaId(), answer, request); return true; }
                        catch (ApiException invalid) { return false; }
                    }).collect(java.util.stream.Collectors.toList()));
            int accepted = 0;
            for (Future<Boolean> result : results) { if (result.get()) { accepted++; } }
            assertThat(accepted).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void wrongIdConsumesOnlyOwnLatestChallenge() {
        CaptchaChallenge original = service.issue(request);
        MockHttpServletRequest other = new MockHttpServletRequest(); other.setRemoteAddr("127.0.0.2");
        CaptchaChallenge foreign = service.issue(other);
        assertThatThrownBy(() -> service.verify(foreign.getCaptchaId(), code.get(), request)).isInstanceOf(ApiException.class);
        assertThat(rows).doesNotContainKey(original.getCaptchaId()).containsKey(foreign.getCaptchaId());
        service.verify(foreign.getCaptchaId(), code.get(), other);
    }
    @Test void refreshChangesIdAndAnswerDigestWhileRetainingSessionBinding() {
        service.issue(request); CaptchaRecord first = rows.values().iterator().next();
        service.issue(request); CaptchaRecord second = rows.values().iterator().next();
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getAnswerHash()).isNotEqualTo(second.getAnswerHash());
        assertThat(first.getBinding()).isEqualTo(second.getBinding());
    }
    @Test void invalidLifetimeFailsConfiguration() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setTtl(java.time.Duration.ofSeconds(301));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
