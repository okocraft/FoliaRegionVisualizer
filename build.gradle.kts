plugins {
    `java-library`
    alias(libs.plugins.paperweight.userdev)
}

group = "net.okocraft.foliaregionvisualizer"
version = "1.0"

val mcVersion = libs.versions.folia.get().replaceAfter(".build", "").removeSuffix(".build")
val fullVersion = "${version}-mc${mcVersion}"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.bluecolored.de/releases")
    }
}

dependencies {
    paperweight.foliaDevBundle(libs.versions.folia.get())
    compileOnly(libs.bluemap.api)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
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
