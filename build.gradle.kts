plugins {
    java
}

group = "nay.amethyst"
version = "1.2.1"

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

val bundled: Configuration by configurations.creating

dependencies {
    implementation(project(":amethyst-simulation"))
    bundled(project(":amethyst-simulation"))
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

tasks.jar {
    dependsOn(bundled)
    from({ bundled.map { zipTree(it) } })
}
