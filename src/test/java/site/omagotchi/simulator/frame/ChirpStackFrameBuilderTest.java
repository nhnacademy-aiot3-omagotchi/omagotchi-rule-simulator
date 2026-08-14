package site.omagotchi.simulator.frame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.simulator.config.SimulatorProperties.SimSensor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChirpStackFrameBuilderTest {

    private final ChirpStackFrameBuilder builder = new ChirpStackFrameBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SimSensor sensor(String point) {
        return new SimSensor("sim-0001", "1층", point, "temperature",
                25.0, 0.5, 1.0, null, null);
    }

    @Test
    @DisplayName("필수 필드(devEui, fCnt, 측정값, location)가 정확히 채워진다")
    void build_fillsRequiredFields() throws Exception {
        SimSensor sensor = sensor(null);

        String json = builder.build(sensor, 7L, 25.3, Instant.now());
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("fCnt").asLong()).isEqualTo(7L);
        assertThat(root.get("fPort").asInt()).isEqualTo(85);
        assertThat(root.get("deviceInfo").get("devEui").asText()).isEqualTo("sim-0001");
        assertThat(root.get("deviceInfo").get("deviceName").asText()).isEqualTo("sim-0001");
        assertThat(root.get("deviceInfo").get("tags").get("location").asText()).isEqualTo("1층");
        assertThat(root.get("object").get("temperature").asDouble()).isEqualTo(25.3);
    }

    @Test
    @DisplayName("point가 null이면 tags에 point 필드 자체가 없다")
    void build_pointNull_omitsPointTag() throws Exception {
        SimSensor sensor = sensor(null);

        String json = builder.build(sensor, 1L, 25.0, Instant.now());
        JsonNode tags = objectMapper.readTree(json).get("deviceInfo").get("tags");

        assertThat(tags.has("point")).isFalse();
    }

    @Test
    @DisplayName("point가 빈 문자열(공백)이면 tags에 point 필드가 없다")
    void build_pointBlank_omitsPointTag() throws Exception {
        SimSensor sensor = sensor("   ");

        String json = builder.build(sensor, 1L, 25.0, Instant.now());
        JsonNode tags = objectMapper.readTree(json).get("deviceInfo").get("tags");

        assertThat(tags.has("point")).isFalse();
    }

    @Test
    @DisplayName("point가 채워져 있으면 tags에 그대로 들어간다")
    void build_pointPresent_includesPointTag() throws Exception {
        SimSensor sensor = sensor("책상");

        String json = builder.build(sensor, 1L, 25.0, Instant.now());
        JsonNode tags = objectMapper.readTree(json).get("deviceInfo").get("tags");

        assertThat(tags.get("point").asText()).isEqualTo("책상");
    }

    @Test
    @DisplayName("measuredTime이 time 필드와 rxInfo.nsTime에 그대로 반영된다")
    void build_measuredTime_reflectedInTimeAndRxInfo() throws Exception {
        SimSensor sensor = sensor(null);
        Instant measuredTime = Instant.parse("2026-08-14T03:00:00Z");

        String json = builder.build(sensor, 1L, 25.0, measuredTime);
        JsonNode root = objectMapper.readTree(json);

        assertThat(Instant.parse(root.get("time").asText())).isEqualTo(measuredTime);
        assertThat(Instant.parse(root.get("rxInfo").get(0).get("nsTime").asText())).isEqualTo(measuredTime);
    }

    @Test
    @DisplayName("deduplicationId는 매번 파싱 가능한 UUID로 채워진다")
    void build_deduplicationId_isValidUuid() throws Exception {
        SimSensor sensor = sensor(null);

        String json = builder.build(sensor, 1L, 25.0, Instant.now());
        String id = objectMapper.readTree(json).get("deduplicationId").asText();

        assertThat(UUID.fromString(id)).isNotNull();
    }
}