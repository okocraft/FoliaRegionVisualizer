plugins {
    alias(libs.plugins.jcommon)
    alias(libs.plugins.paperweight.userdev)
}

group = "net.okocraft.foliaregionvisualizer"
version = "1.0"

val mcVersion = libs.versions.folia.get().replaceAfter(".build", "").removeSuffix(".build")
val fullVersion = "${version}-mc${mcVersion}"

repositories {
    maven {
        url = uri("https://repo.bluecolored.de/releases")
    }
}

jcommon {
    javaVersion = JavaVersion.VERSION_25

    setupJUnit(libs.junit.bom)
    setupMockito(libs.mockito)

    commonDependencies {
        compileOnly(libs.bluemap.api)

        testImplementation(libs.junit.jupiter)
        testImplementation(libs.bluemap.api)
    }
}

dependencies {
    paperweight.foliaDevBundle(libs.versions.folia.get())
}

tasks {
    jar {
        archiveFileName = "FoliaRegionVisualizer-${fullVersion}.jar"
    }

    processResources {
        filesMatching(listOf("plugin.yml")) {
            expand("projectVersion" to fullVersion)
        }
    }
}
