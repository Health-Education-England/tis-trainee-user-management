plugins {
  java
  id("org.springframework.boot") version "3.3.1"
  id("io.spring.dependency-management") version "1.1.5"

  // Code quality plugins
  checkstyle
  jacoco
  id("org.sonarqube") version "5.1.0.4872"
}

group = "uk.nhs.tis.trainee"
version = "1.7.0"

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
    mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:3.1.1")
    mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.2")
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
  val sentryVersion = "7.11.0"
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")
  implementation("io.sentry:sentry-logback:$sentryVersion")

  // Amazon AWS
  implementation("software.amazon.awssdk:cognitoidentityprovider")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
  implementation("com.amazonaws:aws-xray-recorder-sdk-spring:2.16.0")

  testImplementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
  testImplementation("com.playtika.testcontainers:embedded-redis:3.1.7")
  testImplementation("org.testcontainers:junit-jupiter:1.19.8")
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
