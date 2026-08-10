package site.omagotchi.simulator.frame;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import site.omagotchi.simulator.config.SimulatorProperties;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** ChirpStack v4 JSON 생성
 * 엔진(NormalizerNode)이 실제로 읽는 필드만 채운다*/
@Component
public class ChirpStackFrameBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String build(SimulatorProperties.SimSensor sensor, long fCnt, double value, Instant measuredTime) {

        ObjectNode root = objectMapper.createObjectNode();
        root.put("deduplicationId", UUID.randomUUID().toString());
        String timeIso = DateTimeFormatter.ISO_INSTANT.format(measuredTime);
        root.put("time", timeIso);

        ObjectNode deviceInfo = root.putObject("deviceInfo");
        deviceInfo.put("deviceProfileName", "sim-profile");
        deviceInfo.put("deviceName", sensor.devEui());
        deviceInfo.put("devEui", sensor.devEui());

        ObjectNode tags = deviceInfo.putObject("tags");
        tags.put("location", sensor.location());
        if (sensor.point() != null && !sensor.point().isBlank()) {
            tags.put("point", sensor.point());
        }

        root.put("fCnt", fCnt);
        root.put("fPort", 85);

        ObjectNode object = root.putObject("object");
        object.put(sensor.measurement(), value);

        ArrayNode rxInfoArray = root.putArray("rxInfo");
        ObjectNode rxInfo = rxInfoArray.addObject();
        rxInfo.put("gatewayId", "sim-gateway");
        rxInfo.put("nsTime", timeIso);

        return root.toString();
    }
}
