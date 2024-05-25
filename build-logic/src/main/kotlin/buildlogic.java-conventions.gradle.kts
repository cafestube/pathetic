plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }

    maven {
        url = uri("https://repo.codemc.org/repository/nms/")
    }
}

group = "org.patheloper.cafestube"
version = "2.4.3"

java.targetCompatibility = JavaVersion.VERSION_21
java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

publishing {
    repositories {
        maven {
            name = "cafestubeRepository"
            credentials(PasswordCredentials::class)
            url = uri("https://repo.cafestu.be/repository/maven-releases/")
        }
    }

    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
