// sdk — 스크립트 SDK (순수 자바 lib). 사용자가 상속/사용하는 라이프사이클 + Context API.
// 무거운 의존성 없이 유지: JDT LS 클래스패스와 러너 양쪽에서 재사용 (ADR-0003).
plugins {
    `java-library`
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
