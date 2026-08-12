package site.omagotchi.simulator.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import site.omagotchi.simulator.fault.FaultType;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        String brokerUrl,
        String applicationId,
        Integer publisherThreads,   //부하 테스트용
        @Valid
        List<SimSensor> sensors
) {
    public record SimSensor(
            String devEui,
            String location,
            String point,
            String measurement,
            double baseValue,       // 이 값 근처에서 흔들리는 그럴듯한 값 생성
            double jitter,          // 흔들림 폭
            @Positive
            double periodSeconds,      // 발행 주기
            @Valid
            List<Fault> faults,      // 불량 주입 구간 목록
            @Positive
            Integer count           // 센서 대수 (부하 테스트용)
    ) {}

    public record Fault(
            FaultType type,
            @PositiveOrZero
            long fromTick,
            @Positive
            long count
    ) {}
}