package site.omagotchi.simulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        String brokerUrl,           //yaml의 단일 값
        String applicationId,       //yaml의 단일 값
        List<SimSensor> sensors
) {
    public record SimSensor(
            String devEui,
            String location,
            String point,
            String measurement,
            double baseValue,       // 이 값 근처에서 흔들리는 그럴듯한 값 생성
            double jitter,          // 흔들림 폭
            int periodSeconds       // 발행 주기 (실제로는 ±10% 흔들어 발행)
    ) {}
}