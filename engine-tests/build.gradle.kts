// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

// Engine tests are split out due to otherwise quirky project dependency issues with module tests extending engine tests
plugins {
    id("java-library")
    id("org.jetbrains.gradle.plugin.idea-ext")
    id("terasology-common")
}

// Grab all the common stuff like plugins to use, artifact repositories, code analysis config
apply(from = "$rootDir/config/gradle/publish.gradle")

// Read environment variables, including variables passed by jenkins continuous integration server
val env = System.getenv()

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Java Section                                                                                                      //
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Read the internal version out of the engine-tests module.txt
val moduleFile = layout.projectDirectory.file("src/main/resources/org/terasology/unittest/module.txt").asFile

println("Scanning for version in module.txt for engine-tests")
val moduleConfig = groovy.json.JsonSlurper().parseText(moduleFile.readText()) as Map<String, String>

// Gradle uses the magic version variable when creating the jar name (unless explicitly set differently)
version = moduleConfig["version"]!!

// Jenkins-Artifactory integration catches on to this as part of the Maven-type descriptor
group = "org.terasology.engine"

println("Version for $project.name loaded as $version for group $group")

configure<SourceSetContainer> {
    // Adjust output path (changed with the Gradle 6 upgrade, this puts it back)
    main { java.destinationDirectory.set(layout.buildDirectory.dir("classes")) }
    test { java.destinationDirectory.set(layout.buildDirectory.dir("testClasses")) }
}

// Primary dependencies definition
dependencies {
    // Dependency on the engine itself
    implementation(project(":engine"))

    // Dependency not provided for modules, but required for module-tests
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.codehaus.plexus:plexus-utils:3.0.16")
    implementation("com.google.protobuf:protobuf-java:3.16.1")
    implementation("org.terasology:reflections:0.9.12-MB")

    implementation("org.terasology.joml-ext:joml-test:0.1.0")

    testImplementation("ch.qos.logback:logback-classic:1.4.14") {
        because("implementation: a test directly uses logback.classic classes")
    }


    // Test lib dependencies
    implementation(platform("org.junit:junit-bom:5.10.1")) {
        // junit-bom will set version numbers for the other org.junit dependencies.
    }
    api("org.junit.jupiter:junit-jupiter-api") {
        because("we export jupiter Extensions for module tests")
    }
    api("com.google.truth:truth:1.1.3") {
        because("we provide some helper classes")
    }
    implementation("org.mockito:mockito-core:5.6.0") {
        because("classes like HeadlessEnvironment use mocks")
    }
    constraints {
        implementation("net.bytebuddy:bytebuddy:1.14.8") {
            because("we need a newer bytebuddy version for Java 17")
        }
    }

    // See terasology-metrics for other test-only internal dependencies
}

//TODO: Remove it  when gestalt will can to handle ProtectionDomain without classes (Resources)
tasks.register<Copy>("copyResourcesToClasses") {
    from("processResources")
    into(sourceSets["main"].output.classesDirs.first())
}

tasks.named("compileJava") {
    dependsOn("copyResourcesToClasses")
}

tasks.withType<Jar> {
    // Workaround about previous copy to classes. idk why engine-tests:jar called before :engine ...
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Test>("test") {
    dependsOn(tasks.getByPath(":extractNatives"))
    description = "Runs all tests (slow)"
    useJUnitPlatform ()
    systemProperty("junit.jupiter.execution.timeout.default", "4m")
}

tasks.register<Test>("unitTest") {
    dependsOn(tasks.getByPath(":extractNatives"))
    group =  "Verification"
    description = "Runs unit tests (fast)"
    useJUnitPlatform {
        excludeTags = setOf("MteTest", "TteTest")
    }
    systemProperty("junit.jupiter.execution.timeout.default", "1m")
}

tasks.register<Test>("integrationTest") {
    dependsOn(tasks.getByPath(":extractNatives"))
    group = "Verification"
    description = "Runs integration tests (slow) tagged with 'MteTest' or 'TteTest'"

    useJUnitPlatform {
        includeTags = setOf("MteTest", "TteTest")
    }
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
}

idea {
    module {
        // Change around the output a bit
        inheritOutputDirs = false
        outputDir = file("build/classes")
        testOutputDir = file("build/testClasses")
        isDownloadSources = true
    }
}
