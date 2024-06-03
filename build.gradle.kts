plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

group = "net.okocraft.foliaregionvisualizer"
version = "1.0"

val mcVersion = "1.20.6"
val fullVersion = "${version}-mc${mcVersion}"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.bluecolored.de/releases")
    }
}

dependencies {
    paperweight.foliaDevBundle("$mcVersion-R0.1-SNAPSHOT")
    compileOnly("de.bluecolored.bluemap:BlueMapAPI:2.7.2")
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        filesMatching(listOf("plugin.yml")) {
            expand("projectVersion" to fullVersion)
        }
    }

    jar {
        archiveFileName = "FoliaRegionVisualizer-${fullVersion}.jar"
    }
}
