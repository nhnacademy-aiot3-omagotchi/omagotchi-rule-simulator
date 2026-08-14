package site.omagotchi.simulator.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerTest {

    @Test
    @DisplayName("recordPublish를 한 번 호출하면 해당 device:measurement 카운트가 1")
    void recordPublish_once_countsOne() {
        Ledger ledger = new Ledger();

        ledger.recordPublish("sim-0001", "temperature");

        assertThat(ledger.getCount("sim-0001", "temperature")).isEqualTo(1);
    }

    @Test
    @DisplayName("recordPublish를 여러 번 호출하면 누적된다")
    void recordPublish_multipleTimes_accumulates() {
        Ledger ledger = new Ledger();

        ledger.recordPublish("sim-0001", "temperature");
        ledger.recordPublish("sim-0001", "temperature");
        ledger.recordPublish("sim-0001", "temperature");

        assertThat(ledger.getCount("sim-0001", "temperature")).isEqualTo(3);
    }

    @Test
    @DisplayName("device나 measurement가 다르면 별개의 카운트로 분리된다")
    void recordPublish_differentKeys_areIndependent() {
        Ledger ledger = new Ledger();

        ledger.recordPublish("sim-0001", "temperature");
        ledger.recordPublish("sim-0001", "co2");
        ledger.recordPublish("sim-0002", "temperature");

        assertThat(ledger.getCount("sim-0001", "temperature")).isEqualTo(1);
        assertThat(ledger.getCount("sim-0001", "co2")).isEqualTo(1);
        assertThat(ledger.getCount("sim-0002", "temperature")).isEqualTo(1);
    }

    @Test
    @DisplayName("기록된 적 없는 키는 0")
    void getCount_neverRecorded_returnsZero() {
        Ledger ledger = new Ledger();

        assertThat(ledger.getCount("sim-9999", "temperature")).isZero();
    }

    @Test
    @DisplayName("recordFault는 발행 카운트와 별개로 집계된다")
    void recordFault_isIndependentFromPublishCount() {
        Ledger ledger = new Ledger();

        ledger.recordPublish("sim-0001", "temperature");
        ledger.recordFault("sim-0001", "ANOMALY");
        ledger.recordFault("sim-0001", "ANOMALY");

        assertThat(ledger.getCount("sim-0001", "temperature")).isEqualTo(1);
        assertThat(ledger.getFaultCount("sim-0001", "ANOMALY")).isEqualTo(2);
    }

    @Test
    @DisplayName("printSummary()는 기록이 없어도 예외 없이 실행된다")
    void printSummary_emptyLedger_doesNotThrow() {
        Ledger ledger = new Ledger();

        ledger.printSummary();
    }

    @Test
    @DisplayName("printSummary()는 기록이 있어도 예외 없이 실행된다")
    void printSummary_withData_doesNotThrow() {
        Ledger ledger = new Ledger();
        ledger.recordPublish("sim-0001", "temperature");
        ledger.recordFault("sim-0001", "ANOMALY");

        ledger.printSummary();
    }
}