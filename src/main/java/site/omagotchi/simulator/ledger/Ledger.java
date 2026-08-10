package site.omagotchi.simulator.ledger;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * "몇 개를 발행했는지" 세는 장부.
 * 나중에 엔진 로그(수집 건수, 품질 이벤트)와 대조해서 채점하는 기준.
 */
@Slf4j
@Component
public class Ledger {

    private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> faultCounts = new ConcurrentHashMap<>();

    //센서+측정항목 당 발행에 몇번 성공했는가?
    public void recordPublish(String device, String measurement) {
        String key = device +":"+ measurement;
        counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    //센서 당 불량 주입을 몇번 했는가?
    public void recordFault(String device, String faultLabel) {
        String key = device + ":" + faultLabel;
        faultCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    @PreDestroy
    void printSummary() {
        log.info("========== 발행 장부 (채점 기준표) ==========");
        long total = 0;
        for (Map.Entry<String, AtomicLong> entry : new TreeMap<>(counts).entrySet()) {
            long count = entry.getValue().get();
            total += count;
            log.info("  {} : {}건", entry.getKey(), count);
        }
        log.info("  합계: {}건", total);
        log.info("---------- 주입 장부 (기대 신고 수) ----------");
        if (faultCounts.isEmpty()) {
            log.info("  (주입 없음)");
        }
        for (Map.Entry<String, AtomicLong> entry : new TreeMap<>(faultCounts).entrySet()) {
            log.info("  {} : {}건", entry.getKey(), entry.getValue().get());
        }
        log.info("=============================================");
    }
}
