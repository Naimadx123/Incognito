plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

fun git(vararg args: String): String? = runCatching {
    val output = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    if (output.result.get().exitValue != 0) return@runCatching null
    output.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

val gitCommit: String? = git("rev-parse", "HEAD")
val gitDirty: Boolean = git("-c", "core.fileMode=false", "status", "--porcelain") != null

if (version.toString().endsWith("-SNAPSHOT") && gitCommit != null) {
    val stamp = gitCommit.take(8) + if (gitDirty) "-dirty" else ""
    version = "${version.toString().removeSuffix("-SNAPSHOT")}-$stamp-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.2.1")
    compileOnly("io.papermc.paper:paper-api:${providers.gradleProperty("paperApiVersion").getOrElse("1.21.8-R0.1-SNAPSHOT")}")
    compileOnly("io.netty:netty-transport:4.2.18.Final")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.1.0")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("org.xerial:sqlite-jdbc:3.53.4.0")
    compileOnly("com.mysql:mysql-connector-j:26.7.0")
    compileOnly("org.postgresql:postgresql:42.7.13")
    compileOnly("redis.clients:jedis:8.0.1")
    testImplementation(kotlin("test"))
}

val targetJava = providers.gradleProperty("targetJava").getOrElse("21")

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(targetJava))
}

java {
    sourceCompatibility = JavaVersion.toVersion(targetJava)
    targetCompatibility = JavaVersion.toVersion(targetJava)
}

tasks {
    jar {
        enabled = false
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(providers.gradleProperty("minecraftVersion").getOrElse("1.21.8"))
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    test {
        useJUnitPlatform()
    }

    shadowJar {
        relocate("org.bstats", "zone.vao.incognito.lib.bstats")
        mergeServiceFiles()
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        archiveFileName.set("Incognito-v${project.version}.jar")
        manifest {
            attributes(
                "Implementation-Version" to project.version.toString(),
                "Git-Commit" to (gitCommit ?: "unknown"),
                "Git-Dirty" to gitDirty.toString(),
            )
        }
        filesMatching("META-INF/*.kotlin_module") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        minimize()
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
