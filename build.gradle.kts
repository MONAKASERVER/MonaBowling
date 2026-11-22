plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.monacraft"
version = "0.6.0-alpha.13"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly("net.citizensnpcs:citizens-main:2.0.43-SNAPSHOT") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.51.1.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
        minHeapSize = "32m"
        maxHeapSize = "192m"
        jvmArgs("-XX:+UseSerialGC", "-XX:ReservedCodeCacheSize=64m")
    }
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("MonaBowling-${project.version}.jar")
        mergeServiceFiles()
    }
    build {
        dependsOn(shadowJar)
    }
}
