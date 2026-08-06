package site.omagotchi.simulator.mqtt;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.stereotype.Component;
import site.omagotchi.simulator.config.SimulatorProperties;

import java.nio.charset.StandardCharsets;

/** 브로커 연결·발행 */
@Slf4j
@Component
public class MqttPublisherClient {

    private final SimulatorProperties simulatorProperties;
    private MqttClient mqttClient;              //동기

    public MqttPublisherClient(SimulatorProperties simulatorProperties) {
        this.simulatorProperties = simulatorProperties;
    }

    @PostConstruct              //생성자 끝나면, @PostConstruct 붙은 메서드를 스프링이 알아서 호출
    void connect() throws MqttException {
        String clientId = "rule-simulator-" + System.currentTimeMillis();
        mqttClient = new MqttClient(simulatorProperties.brokerUrl(), clientId, new MemoryPersistence());    //메모리에 기록을 저장

        MqttConnectionOptions options = new MqttConnectionOptions();
        //브로커와 연결이 끊기면 자동으로 재연결 시도
        options.setAutomaticReconnect(true);

        mqttClient.connect(options);
        log.info("[MqttPublisherClient] 브로커 연결 완료: {}", simulatorProperties.brokerUrl());
    }

    public void publish(String topic, String payload){
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);          //핸드셰이크

            mqttClient.publish(topic,message);
        } catch (MqttException e) {
            log.error("[MqttPublisherClient] 발행 실패: topic={}", topic, e);
        }
    }

    @PreDestroy             //앱을 끌 때 스프링이 알아서 호출
    void disconnect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
    }
}
