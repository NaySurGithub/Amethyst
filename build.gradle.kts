plugins {
    java
}

group = "nay.amethyst"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("../powernukkitx.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
}
