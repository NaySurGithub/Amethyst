plugins {
    java
}

group = "nay.amethyst"
version = "1.1.7"

repositories {
    mavenCentral()
    maven {
        name = "powerNukkitXReleases"
        url = uri("https://repo.powernukkitx.org/releases")
    }
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/") {
        mavenContent {
            snapshotsOnly()
        }
    }
}

dependencies {
    compileOnly("org.powernukkitx:server:stable-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
}
