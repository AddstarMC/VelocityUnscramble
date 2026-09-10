plugins {
    id("java")
    id("com.gradleup.shadow") version "9.6.1"
    id("maven-publish")
}

group = project.property("group") as String
version = project.property("version") as String

val targetJavaVersion = 25
val velocityApiVersion = "4.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://maven.addstar.com.au/artifactory/ext-release-local") { name = "addstar" }
}

dependencies {
    // Velocity brings Adventure/MiniMessage, Configurate, Brigadier and Guava with it.
    // Do not declare those separately - shading a second copy is what caused the
    // classloader pain with Yamler under Snap.
    compileOnly("com.velocitypowered:velocity-api:$velocityApiVersion")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityApiVersion")

    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("com.mysql:mysql-connector-j:26.7.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

// Expands ${version} into BuildConstants so @Plugin can use a compile-time constant.
val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    from(templateSource)
    into(templateDest)
    expand(props)
}
sourceSets.main { java.srcDir(generateTemplates.map { it.outputs }) }

tasks {
    shadowJar {
        archiveClassifier.set("")
        // Relocate Hikari so another proxy plugin's copy can never conflict.
        relocate("com.zaxxer.hikari", "au.com.addstar.velocityunscramble.shaded.hikari")
        // The MySQL driver is deliberately NOT relocated: it registers through
        // META-INF/services/java.sql.Driver, which relocation would break.
        // The driver registers via META-INF/services; make sure the transformer
        // sees every copy instead of silently dropping duplicates.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
    }
    jar { enabled = false }
    build { dependsOn(shadowJar) }
}

publishing {
    publications.create<MavenPublication>("maven") { artifact(tasks.shadowJar) }
    repositories {
        maven {
            name = "addstar"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT"))
                    "https://maven.addstar.com.au/artifactory/ext-snapshot-local"
                else
                    "https://maven.addstar.com.au/artifactory/ext-release-local"
            )
            credentials {
                username = System.getenv("ARTIFACTORY_USER")
                password = System.getenv("ARTIFACTORY_PASSWORD")
            }
        }
    }
}




