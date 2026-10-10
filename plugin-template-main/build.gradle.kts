import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.plugins.jvm.JvmTestSuite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    checkstyle
    id("com.github.spotbugs") version "6.5.11"
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    java
}

group = "com.crimsonwarpedcraft.exampleplugin"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyMMdd-HHmm")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

version = (if (!hasProperty("ver")) {
    "0-5"
} else {
    val ver = property("ver") as String
    val base = if (ver.startsWith("v")) ver.drop(1) else ver.replace('/', '-')
    if (ver.startsWith("v") && !ver.lowercase().contains("-rc-")) base else "$base-SNAPSHOT"
}).uppercase()

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("net.md-5", "bungeecord-chat")
            includeGroup("io.papermc.adventure")
        }
    }

    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }

    mavenCentral()

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.CrimsonWarpedcraft")
        }
    }
}

val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    // Compile against the oldest supported Paper API to keep the plugin compatible with 1.21+.
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")

    // Code quality and unit testing. Not required for code functionality.
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")
    spotbugsPlugins("com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:4.10.4")
    testImplementation("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")

    testImplementation("org.mockito:mockito-core:5.24.0")
    mockitoAgent("org.mockito:mockito-core:5.24.0") { isTransitive = false }
}

// Exclude the unused plugin-template example from the actual checkout plugin build.
sourceSets {
    main {
        java {
            exclude("**/ExamplePlugin.java", "**/command/**", "**/config/**", "**/data/**", "**/listener/**")
        }
    }
    test {
        java { exclude("**/*") }
    }
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
}

tasks.runServer {
    minecraftVersion("1.21.11")
}

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter("6.1.1")

            dependencies {
                implementation(sourceSets.main.get().output)
                implementation("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(tasks.test)
                        jvmArgs("--enable-native-access=ALL-UNNAMED")
                    }
                }
            }
        }
    }
}

sourceSets.named("integrationTest") {
    java { exclude("**/*") }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get())
}

tasks.processResources {
    filesMatching("**/plugin.yml") {
        expand(mapOf("NAME" to rootProject.name, "VERSION" to version, "PACKAGE" to project.group))
    }
}

checkstyle {
    toolVersion = "14.0.0"
    maxWarnings = 100
}

configurations.named("checkstyle") {
    resolutionStrategy.capabilitiesResolution
        .withCapability("com.google.collections:google-collections") {
            select("com.google.guava:guava:23.0")
        }
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
    }
    reports.create("xml") {
        required.set(false)
    }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("giipscheckout-${version.toString().replace('.', '-')}.jar")
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(shadowJar)
}

tasks.register("printProjectName") {
    doLast {
        println(rootProject.name)
    }
}

tasks.register("release") {
    dependsOn("build")

    doLast {
        if (!version.toString().endsWith("-SNAPSHOT")) {
            // shadowJar already writes the versioned giipscheckout artifact.
        }
    }
}
