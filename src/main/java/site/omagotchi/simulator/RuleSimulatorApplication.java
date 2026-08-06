package site.omagotchi.simulator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import site.omagotchi.simulator.config.SimulatorProperties;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
public class RuleSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleSimulatorApplication.class, args);
    }

    // Bean 등록 해놓으면 스프링이 앱이 다 켜진 직후 딱 한 번 실행
    // 설정이 제대로 바인딩되는지 기동 로그 확인용
    @Bean
    CommandLineRunner logLoadedConfig(SimulatorProperties properties) {
        return args -> {
            log.info("브로커: {}", properties.brokerUrl());
            log.info("가짜 센서 {}대 로드", properties.sensors().size());
            properties.sensors().forEach(sensor ->
                    log.info("  {} | {}/{} | {} | 주기 {}초 | 기준값 {}",
                            sensor.devEui(), sensor.location(), sensor.point(),
                            sensor.measurement(), sensor.periodSeconds(), sensor.baseValue()));
        };
    }
}