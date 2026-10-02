// Canonical build definition for bigpc / CI.
// The sandbox cannot run the Gradle daemon (loopback TCP is intercepted),
// so local sandbox builds use build.sh (direct javac). Keep both in sync.
plugins {
    java
}

group = "com.forgeplugins"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.35-alpha")
    compileOnly("org.jetbrains:annotations:26.0.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
    options.compilerArgs.add("-Werror")
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-parameters")
}

tasks.jar {
    archiveBaseName.set("ForgeWorldGen")
}
