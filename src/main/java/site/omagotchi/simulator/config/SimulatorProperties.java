package site.omagotchi.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import site.omagotchi.simulator.fault.FaultType;

import java.util.List;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        String brokerUrl,
        String applicationId,
        Integer publisherThreads,   //부하 테스트용
        List<SimSensor> sensors
) {
    public record SimSensor(
            String devEui,
            String location,
            String point,
            String measurement,
            double baseValue,       // 이 값 근처에서 흔들리는 그럴듯한 값 생성
            double jitter,          // 흔들림 폭
            double periodSeconds,      // 발행 주기
            List<Fault> faults,      // 불량 주입 구간 목록
            Integer count           // 센서 대수 (부하 테스트용)
    ) {}

    public record Fault(
            FaultType type,
            long fromTick,
            long count
    ) {}
}