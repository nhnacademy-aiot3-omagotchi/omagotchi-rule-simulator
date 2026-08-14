package site.omagotchi.simulator.mqtt;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.simulator.config.SimulatorProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqttPublisherClientTest {

    @Mock
    MqttClient mqttClient;

    private MqttPublisherClient newClient() {
        SimulatorProperties properties = new SimulatorProperties(
                "tcp://localhost:1883", "test-app", 4, List.of());
        MqttPublisherClient client = new MqttPublisherClient(properties);
        client.setMqttClient(mqttClient);
        return client;
    }

    @Test
    @DisplayName("발행 성공하면 true를 반환하고 실패 카운트는 0 그대로")
    void publish_success_returnsTrueAndKeepsFailureCountZero() {
        MqttPublisherClient client = newClient();

        boolean result = client.publish("topic", "payload");

        assertThat(result).isTrue();
        assertThat(client.getFailureCount()).isZero();
    }

    @Test
    @DisplayName("발행 실패하면 false를 반환하고 실패 카운트가 1 증가")
    void publish_failure_returnsFalseAndIncrementsFailureCount() throws MqttException {
        MqttPublisherClient client = newClient();
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mqttClient).publish(any(), any());

        boolean result = client.publish("topic", "payload");

        assertThat(result).isFalse();
        assertThat(client.getFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연속 50번 실패하면 실패 카운트가 정확히 50 (rate-limit 로그가 몇 번째마다 찍히는지의 기준값)")
    void publish_50ConsecutiveFailures_countsAllFifty() throws MqttException {
        MqttPublisherClient client = newClient();
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(mqttClient).publish(any(), any());

        for (int i = 0; i < 50; i++) {
            client.publish("topic", "payload");
        }

        assertThat(client.getFailureCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("연결 상태면 disconnect() 호출 시 실제로 끊는다")
    void disconnect_whenConnected_callsMqttClientDisconnect() throws MqttException {
        MqttPublisherClient client = newClient();
        when(mqttClient.isConnected()).thenReturn(true);

        client.disconnect();

        verify(mqttClient).disconnect();
    }

    @Test
    @DisplayName("이미 끊긴 상태면 disconnect()를 호출해도 아무 일도 안 한다")
    void disconnect_whenNotConnected_doesNothing() throws MqttException {
        MqttPublisherClient client = newClient();
        when(mqttClient.isConnected()).thenReturn(false);

        client.disconnect();

        verify(mqttClient, never()).disconnect();
    }
}