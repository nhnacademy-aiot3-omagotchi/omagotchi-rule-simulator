package site.omagotchi.simulator.publish;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.simulator.config.SimulatorProperties;
import site.omagotchi.simulator.config.SimulatorProperties.SimSensor;
import site.omagotchi.simulator.frame.ChirpStackFrameBuilder;
import site.omagotchi.simulator.mqtt.MqttPublisherClient;

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

    public SensorScheduler(SimulatorProperties properties, MqttPublisherClient mqttPublisherClient,
                           ChirpStackFrameBuilder frameBuilder) {
        this.properties = properties;
        this.mqttPublisherClient = mqttPublisherClient;
        this.frameBuilder = frameBuilder;
    }

    @PostConstruct
    void start() {
        for (SimSensor sensor : properties.sensors()) {
            AtomicLong fCnt = new AtomicLong(0);
            publish(sensor, fCnt);
            scheduleNext(sensor, fCnt);
        }
    }

    private void scheduleNext(SimSensor sensor, AtomicLong fCnt) {
        long delayMillis = jitteredDelayMillis(sensor.periodSeconds());
        executor.schedule(() -> {
            publish(sensor, fCnt);
            scheduleNext(sensor, fCnt);   // 스스로를 다시 예약 (자기재귀 스케줄링)
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void publish(SimSensor sensor, AtomicLong fCnt) {
        double value = sensor.baseValue() + (random.nextDouble() * 2 - 1) * sensor.jitter();
        long currentFcnt = fCnt.getAndIncrement();

        String topic = "application/" + properties.applicationId() + "/device/" + sensor.devEui() + "/event/up";
        String payload = frameBuilder.build(sensor, currentFcnt, value);

        mqttPublisherClient.publish(topic, payload);
        log.info("[발행] {} fCnt={} {}={}", sensor.devEui(), currentFcnt, sensor.measurement(),
                String.format("%.2f", value));
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