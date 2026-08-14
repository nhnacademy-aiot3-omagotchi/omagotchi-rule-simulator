package site.omagotchi.simulator.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.simulator.config.SimulatorProperties.SimSensor;
import site.omagotchi.simulator.fault.FaultType;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorPropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private SimSensor validSensor() {
        return new SimSensor("sim-test", "loc", null, "temperature",
                25.0, 0.5, 1.0, null, null);
    }

    @Test
    @DisplayName("periodSeconds가 0이면 검증 실패 (회귀 테스트)")
    void periodSecondsZero_failsValidation() {
        SimSensor sensor = new SimSensor("sim-test", "loc", null, "temperature",
                25.0, 0.5, 0.0, null, null);
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of(sensor));

        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("periodSeconds가 양수면 검증 통과")
    void periodSecondsPositive_passesValidation() {
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of(validSensor()));

        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("count가 음수면 검증 실패")
    void countNegative_failsValidation() {
        SimSensor sensor = new SimSensor("sim-test", "loc", null, "temperature",
                25.0, 0.5, 1.0, null, -5);
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of(sensor));

        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("count가 null이면 검증 통과 (생략 허용)")
    void countNull_passesValidation() {
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of(validSensor()));

        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("fault의 fromTick이 음수면 검증 실패")
    void faultFromTickNegative_failsValidation() {
        SimulatorProperties.Fault fault = new SimulatorProperties.Fault(FaultType.ANOMALY, -1L, 3L);
        SimSensor sensor = new SimSensor("sim-test", "loc", null, "temperature",
                25.0, 0.5, 1.0, List.of(fault), null);
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of(sensor));

        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("fault의 fromTick이 0이면 검증 통과 (0부터 시작 가능)")
    void faultFromTickZero_passesValidation() {
        SimulatorProperties.Fault fault = new SimulatorProperties.Fault(FaultType.ANOMALY, 0L, 3L);
        SimSensor sensor = new SimSensor("sim-test", "loc", null, "temperature",
                25.0, 0.5, 1.0, List.of(fault), null);
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of(sensor));

        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }
}