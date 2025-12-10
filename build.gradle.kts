plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
}

group = "net.okocraft.foliaregionvisualizer"
version = "1.0"

val mcVersion = "1.21.8"
val fullVersion = "${version}-mc${mcVersion}"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.bluecolored.de/releases")
    }
}

dependencies {
    paperweight.foliaDevBundle("$mcVersion-R0.1-SNAPSHOT")
    compileOnly("de.bluecolored:bluemap-api:2.7.7")
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
