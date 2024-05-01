plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "net.okocraft.foliaregionvisualizer"
version = "1.0"

val mcVersion = "1.20.4"
val fullVersion = "${version}-mc${mcVersion}"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    paperweight.foliaDevBundle("$mcVersion-R0.1-SNAPSHOT")
    compileOnly("com.github.BlueMap-Minecraft:BlueMapAPI:2.7.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    reobfJar {
        outputJar.set(
                project.layout.buildDirectory
                        .file("libs/FoliaRegionVisualizer-${fullVersion}.jar")
        )
    }

    build {
        dependsOn(reobfJar)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        filesMatching(listOf("plugin.yml")) {
            expand("projectVersion" to fullVersion)
        }
    }
}
