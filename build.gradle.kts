plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)

  // Code quality plugins
  checkstyle
  jacoco
  alias(libs.plugins.sonarqube)
}

group = "uk.nhs.tis.trainee"
version = "2.4.3"

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

dependencyManagement {
  imports {
    mavenBom(libs.spring.cloud.dependencies.aws.get().toString())
    mavenBom(libs.spring.cloud.dependencies.core.get().toString())
  }
}

dependencies {
  // Spring Boot starters
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-security")

  implementation("com.transformuk.hee:tis-security-jwt:6.0.0-SNAPSHOT")
  implementation("com.transformuk.hee:profile-client:3.4.1") {
    exclude("com.fasterxml.jackson.module", "jackson-module-jaxb-annotations")
  }

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // MapStruct
  implementation(libs.mapstruct.core)
  annotationProcessor(libs.mapstruct.processor)
  testAnnotationProcessor(libs.mapstruct.processor)

  // Sentry reporting
  implementation(libs.bundles.sentry)

  // Amazon AWS
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
  implementation("software.amazon.awssdk:cognitoidentityprovider")
  implementation(libs.aws.xray.spring)

  //Amazon Cloudwatch
  implementation("io.micrometer:micrometer-core")
  implementation("io.micrometer:micrometer-registry-cloudwatch2")

  //Amazon Cloudwatch
  implementation("io.micrometer:micrometer-core")
  implementation("io.micrometer:micrometer-registry-cloudwatch2")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
  testImplementation("com.playtika.testcontainers:embedded-redis:3.1.15")
  testImplementation("org.testcontainers:junit-jupiter")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

checkstyle {
  config = resources.text.fromArchiveEntry(configurations.checkstyle.get().first(), "google_checks.xml")
}

sonarqube {
  properties {
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.login", System.getenv("SONAR_TOKEN"))
    property("sonar.organization", "health-education-england")
    property("sonar.projectKey", "Health-Education-England_tis-trainee-user-management")

    property("sonar.java.checkstyle.reportPaths",
      "build/reports/checkstyle/main.xml,build/reports/checkstyle/test.xml")
  }
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
  useJUnitPlatform()
}
