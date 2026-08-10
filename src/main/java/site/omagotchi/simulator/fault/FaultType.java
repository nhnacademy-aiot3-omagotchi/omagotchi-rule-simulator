package site.omagotchi.simulator.fault;

/** 주입 가능한 불량 종류 */
public enum FaultType {
    ANOMALY,      // 이상치
    DUPLICATE,    // 중복
    DELAYED,      // 지연
    STUCK,        // 무변동
    DISCONNECT,   // 끊김
    MISSING       // 결측
}