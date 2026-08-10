package site.omagotchi.simulator.publish;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.simulator.config.SimulatorProperties;
import site.omagotchi.simulator.config.SimulatorProperties.SimSensor;
import site.omagotchi.simulator.fault.FaultType;
import site.omagotchi.simulator.frame.ChirpStackFrameBuilder;
import site.omagotchi.simulator.ledger.Ledger;
import site.omagotchi.simulator.mqtt.MqttPublisherClient;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class SensorScheduler {

    private final SimulatorProperties properties;
    private final MqttPublisherClient mqttPublisherClient;
    private final ChirpStackFrameBuilder frameBuilder;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final Random random = new Random();
    private final Ledger ledger;
    /** 지연 주입 폭: 측정 시각을 5분 과거로 */
    private static final long DELAY_SECONDS = 300;

    public SensorScheduler(SimulatorProperties properties, MqttPublisherClient mqttPublisherClient,
                           ChirpStackFrameBuilder frameBuilder, Ledger ledger) {
        this.properties = properties;
        this.mqttPublisherClient = mqttPublisherClient;
        this.frameBuilder = frameBuilder;
        this.ledger = ledger;
    }

    @PostConstruct
    void start() {
        for (SimSensor sensor : properties.sensors()) {
            AtomicLong fCnt = new AtomicLong(0);            //실제 발행
            AtomicLong tick = new AtomicLong(0);            //건너뛴 것까지 표시
            publish(sensor, tick, fCnt);
            scheduleNext(sensor,tick ,fCnt);
        }
    }

    private void scheduleNext(SimSensor sensor, AtomicLong tick, AtomicLong fCnt) {
        long delayMillis = jitteredDelayMillis(sensor.periodSeconds());
        executor.schedule(() -> {
            publish(sensor, tick, fCnt);
            scheduleNext(sensor, tick, fCnt);   // 스스로를 다시 예약 (자기재귀 스케줄링)
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void publish(SimSensor sensor, AtomicLong tick, AtomicLong fCnt) {
        long currentTick = tick.getAndIncrement();
        SimulatorProperties.Fault fault = activeFault(sensor, currentTick);
        FaultType faultType = fault != null ? fault.type() : null;
        boolean windowStart = fault != null && currentTick == fault.fromTick();

        // 끊김: 발행 자체를 생략. fCnt도 정지
        if (faultType == FaultType.DISCONNECT) {
            if (windowStart) {
                ledger.recordFault(sensor.devEui(), "DISCONNECT(구간)");
            }
            log.info("[주입] {} 끊김 - 발행 생략 (차례 {})", sensor.devEui(), currentTick);
            return;
        }

        // 결측: 프레임 번호를 하나 건너뛴다
        if (faultType == FaultType.MISSING) {
            long skippedFcnt = fCnt.getAndIncrement();
            log.info("[주입] {} 결측 - fCnt {} 건너뜀", sensor.devEui(), skippedFcnt);
        }

        // 값 결정
        double value;
        if (faultType == FaultType.ANOMALY) {
            value = sensor.baseValue() * 100;               // 물리 범위 확실히 밖
        } else if (faultType == FaultType.STUCK) {
            value = sensor.baseValue();                      // 흔들림 없이 고정
        } else {
            value = sensor.baseValue() + (random.nextDouble() * 2 - 1) * sensor.jitter();
        }

        // 시각 결정
        Instant measuredTime = (faultType == FaultType.DELAYED)
                ? Instant.now().minusSeconds(DELAY_SECONDS)
                : Instant.now();

        long currentFcnt = fCnt.getAndIncrement();
        String topic = "application/" + properties.applicationId() + "/device/" + sensor.devEui() + "/event/up";
        String payload = frameBuilder.build(sensor, currentFcnt, value, measuredTime);

        boolean success = mqttPublisherClient.publish(topic, payload);
        if (success) {
            ledger.recordPublish(sensor.devEui(), sensor.measurement());
            // 주입 기록은 발행에 성공한 것만 센다
            if (faultType == FaultType.ANOMALY) {
                ledger.recordFault(sensor.devEui(), "ANOMALY");
            } else if (faultType == FaultType.DELAYED) {
                ledger.recordFault(sensor.devEui(), "DELAYED");
            } else if(faultType == FaultType.MISSING) {
                ledger.recordFault(sensor.devEui(), "MISSING");
            } else if (faultType == FaultType.STUCK && windowStart) {
                ledger.recordFault(sensor.devEui(), "STUCK(구간)");
            } else if (faultType == FaultType.DUPLICATE) {
                // 중복: 같은 프레임(같은 fCnt·payload)을 그대로 한 번 더 발행
                boolean dupSuccess = mqttPublisherClient.publish(topic, payload);
                if (dupSuccess) {
                    ledger.recordFault(sensor.devEui(), "DUPLICATE");
                }
            }
        }

        log.info("[발행] {} 차례={} fCnt={} {}={}{}",
                sensor.devEui(), currentTick, currentFcnt, sensor.measurement(),
                String.format("%.2f", value),
                faultType != null ? " [주입:" + faultType + "]" : "");
    }

    /** 현재 tick이 불량 구간인가? 맞다면 fault를 리턴 */
    private SimulatorProperties.Fault activeFault(SimSensor sensor, long tick) {
        if (sensor.faults() == null) {
            return null;
        }
        for (SimulatorProperties.Fault fault : sensor.faults()) {
            if (tick >= fault.fromTick() && tick < fault.fromTick() + fault.count()) {
                return fault;
            }
        }
        return null;
    }

    private long jitteredDelayMillis(int periodSeconds) {
        double jitterRatio = 0.9 + random.nextDouble() * 0.2;   // 0.9~1.1
        return Math.round(periodSeconds * 1000 * jitterRatio);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }
}