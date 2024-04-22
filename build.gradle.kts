plugins {
  java
  id("org.springframework.boot") version "3.2.5"
  id("io.spring.dependency-management") version "1.1.4"

  // Code quality plugins
  checkstyle
  jacoco
  id("org.sonarqube") version "4.4.1.3373"
}

group = "uk.nhs.tis.trainee"
version = "1.4.0"

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()

  maven {
    url = uri("https://hee-430723991443.d.codeartifact.eu-west-1.amazonaws.com/maven/Health-Education-England/")
    credentials {
      username = "aws"
      password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
    }
  }
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:2021.0.8")
    mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:2.4.4")
  }
}

dependencies {
  // Spring Boot starters
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-security")
  testImplementation("org.springframework.boot:spring-boot-starter-test")

  implementation("com.transformuk.hee:tis-security-jwt:5.1.4")
  implementation("com.transformuk.hee:profile-client:3.1.1")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // MapStruct
  val mapstructVersion = "1.5.5.Final"
  implementation("org.mapstruct:mapstruct:${mapstructVersion}")
  annotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")
  testAnnotationProcessor("org.mapstruct:mapstruct-processor:${mapstructVersion}")

  // Sentry reporting
  val sentryVersion = "7.4.0"
  implementation("io.sentry:sentry-spring-boot-starter:$sentryVersion")
  implementation("io.sentry:sentry-logback:$sentryVersion")

  // Amazon AWS
  implementation("com.amazonaws:aws-java-sdk-cognitoidp")
  implementation("io.awspring.cloud:spring-cloud-starter-aws-messaging")
  implementation("com.amazonaws:aws-xray-recorder-sdk-spring:2.15.1")

  testImplementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
  testImplementation("com.playtika.testcontainers:embedded-redis:2.3.6")
  testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}

checkstyle {
  config = resources.text.fromArchiveEntry(configurations.checkstyle.get().first(), "google_checks.xml")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
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
