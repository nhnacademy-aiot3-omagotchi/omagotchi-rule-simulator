package site.omagotchi.simulator.publish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.simulator.config.SimulatorProperties;
import site.omagotchi.simulator.config.SimulatorProperties.SimSensor;
import site.omagotchi.simulator.fault.FaultType;
import site.omagotchi.simulator.frame.ChirpStackFrameBuilder;
import site.omagotchi.simulator.ledger.Ledger;
import site.omagotchi.simulator.mqtt.MqttPublisherClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SensorSchedulerTest {

    @Mock
    MqttPublisherClient mqttPublisherClient;

    @Mock
    ChirpStackFrameBuilder frameBuilder;

    @Mock
    Ledger ledger;

    private SensorScheduler newScheduler() {
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of());
        return new SensorScheduler(properties, mqttPublisherClient, frameBuilder, ledger);
    }

    private SimSensor sensor(Integer count) {
        return new SimSensor("sim-test", "loc", null, "temperature",
                25.0, 0.5, 1.0, null, count);
    }

    private SimSensor sensorWithFault(double baseValue, double jitter, FaultType faultType) {
        List<SimulatorProperties.Fault> faults = List.of(
                new SimulatorProperties.Fault(faultType, 0L, 1L));
        return new SimSensor("sim-test", "loc", null, "temperature",
                baseValue, jitter, 1.0, faults, null);
    }

    @Test
    @DisplayName("count가 null이면 원본 그대로 1개짜리 리스트")
    void expand_nullCount_returnsSingleOriginal() {
        SensorScheduler scheduler = newScheduler();
        SimSensor original = sensor(null);

        List<SimSensor> result = scheduler.expand(original);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("count가 1이면 원본 그대로 1개짜리 리스트")
    void expand_countOne_returnsSingleOriginal() {
        SensorScheduler scheduler = newScheduler();
        SimSensor original = sensor(1);

        List<SimSensor> result = scheduler.expand(original);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("count가 0이면 원본 그대로 1개짜리 리스트 (방어적 처리)")
    void expand_countZero_returnsSingleOriginal() {
        SensorScheduler scheduler = newScheduler();
        SimSensor original = sensor(0);

        List<SimSensor> result = scheduler.expand(original);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("count가 50이면 devEui에 -001~-050이 붙은 50개로 펼쳐짐")
    void expand_count50_returns50NumberedInstances() {
        SensorScheduler scheduler = newScheduler();
        SimSensor original = sensor(50);

        List<SimSensor> result = scheduler.expand(original);

        assertThat(result).hasSize(50);
        assertThat(result.get(0).devEui()).isEqualTo("sim-test-001");
        assertThat(result.get(49).devEui()).isEqualTo("sim-test-050");
    }

    // ANOMALY 값 테스트
    @Test
    @DisplayName("ANOMALY면 기준값 + 오프셋으로 발행")
    void publish_anomaly_addsOffset() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(25.0, 0.5, FaultType.ANOMALY);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder).build(eq(sensor), anyLong(), eq(25.0 + 1_000_000), any());
    }

    @Test
    @DisplayName("ANOMALY면 기준값이 0이어도 범위 밖 값으로 발행 (회귀 테스트)")
    void publish_anomalyWithZeroBaseValue_stillOutOfRange() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(0.0, 0.5, FaultType.ANOMALY);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder).build(eq(sensor), anyLong(), eq(1_000_000.0), any());
    }

    @Test
    @DisplayName("STUCK이면 흔들림 없이 기준값 그대로 발행")
    void publish_stuck_returnsExactBaseValue() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(25.0, 5.0, FaultType.STUCK);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder).build(eq(sensor), anyLong(), eq(25.0), any());
    }

    @Test
    @DisplayName("정상이면 기준값 ± 흔들림 범위 안에서 발행")
    void publish_normal_withinJitterRange() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensor(null);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder).build(eq(sensor), anyLong(),
                doubleThat(v -> v >= 24.5 && v <= 25.5), any());
    }
    // DUPLICATE
    @Test
    @DisplayName("DUPLICATE면 발행이 2번 나가고 recordPublish도 2번 불림 (회귀 테스트)")
    void publish_duplicate_publishesAndRecordsTwice() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(25.0, 0.5, FaultType.DUPLICATE);
        when(frameBuilder.build(any(), anyLong(), anyDouble(), any())).thenReturn("dummy-payload");
        when(mqttPublisherClient.publish(anyString(), anyString())).thenReturn(true);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(mqttPublisherClient, times(2)).publish(anyString(), anyString());
        verify(ledger, times(2)).recordPublish(sensor.devEui(), sensor.measurement());
        verify(ledger).recordFault(sensor.devEui(), "DUPLICATE");
    }

    // DISCONNECT
    @Test
    @DisplayName("DISCONNECT면 발행 자체를 안 하고, 구간 시작이면 recordFault만 기록")
    void publish_disconnect_skipsPublishing() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(25.0, 0.5, FaultType.DISCONNECT);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder, never()).build(any(), anyLong(), anyDouble(), any());
        verify(mqttPublisherClient, never()).publish(anyString(), anyString());
        verify(ledger).recordFault(sensor.devEui(), "DISCONNECT(구간)");
    }

    // MISSING
    @Test
    @DisplayName("MISSING이면 fCnt를 하나 건너뛰고 발행 (skippedFcnt=0, 실제 발행은 fCnt=1)")
    void publish_missing_skipsOneFcnt() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(25.0, 0.5, FaultType.MISSING);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder).build(eq(sensor), eq(1L), anyDouble(), any());
    }

    // DELAYED
    @Test
    @DisplayName("DELAYED면 측정 시각이 5분 넘게 과거로 찍힘")
    void publish_delayed_setsTimeToPast() {
        SensorScheduler scheduler = newScheduler();
        SimSensor sensor = sensorWithFault(25.0, 0.5, FaultType.DELAYED);

        scheduler.publish(sensor, new AtomicLong(0), new AtomicLong(0));

        verify(frameBuilder).build(eq(sensor), anyLong(), anyDouble(),
                argThat(time -> time.isBefore(java.time.Instant.now().minusSeconds(250))));
    }
}