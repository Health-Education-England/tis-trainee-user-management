rootProject.name = "tis-trainee-user-management"

dependencyResolutionManagement {
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

  versionCatalogs {
    create("libs") {
      from("uk.nhs.tis.trainee:version-catalog:0.0.8")
    }
  }
}
