plugins {
    java
    `maven-publish`
    signing
    id("org.springframework.boot") version "3.5.8"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.neo1228"
version = "0.1.0-SNAPSHOT"
description = "Spring Boot starter that auto-exposes SpringDoc OpenAPI operations as MCP tools"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.3")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    implementation("com.jayway.jsonpath:json-path:2.10.0")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.1.2")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

val ossrhUsername = providers.environmentVariable("OSSRH_USERNAME")
    .orElse(providers.gradleProperty("ossrhUsername"))
    .orNull
val ossrhPassword = providers.environmentVariable("OSSRH_PASSWORD")
    .orElse(providers.gradleProperty("ossrhPassword"))
    .orNull
val githubPackagesUser = providers.environmentVariable("GITHUB_ACTOR")
    .orElse(providers.gradleProperty("gpr.user"))
    .orNull
val githubPackagesToken = providers.environmentVariable("GITHUB_TOKEN")
    .orElse(providers.gradleProperty("gpr.key"))
    .orNull

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "spring-boot-starter-swagger-mcp"

            pom {
                name.set("spring-boot-starter-swagger-mcp")
                description.set(project.description)
                url.set("https://github.com/Neo1228/spring-boot-starter-swagger-mcp")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("Neo1228")
                        name.set("Neo1228")
                        url.set("https://github.com/Neo1228")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Neo1228/spring-boot-starter-swagger-mcp.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Neo1228/spring-boot-starter-swagger-mcp.git")
                    url.set("https://github.com/Neo1228/spring-boot-starter-swagger-mcp")
                }
            }
        }
    }

    repositories {
        if (!ossrhUsername.isNullOrBlank() && !ossrhPassword.isNullOrBlank()) {
            maven {
                name = "OSSRH"
                val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                credentials {
                    username = ossrhUsername
                    password = ossrhPassword
                }
            }
        }

        if (!githubPackagesUser.isNullOrBlank() && !githubPackagesToken.isNullOrBlank()) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Neo1228/spring-boot-starter-swagger-mcp")
                credentials {
                    username = githubPackagesUser
                    password = githubPackagesToken
                }
            }
        }
    }
}

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY")
        .orElse(providers.gradleProperty("signingKey"))
        .orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
        .orElse(providers.gradleProperty("signingPassword"))
        .orNull

    setRequired {
        !version.toString().endsWith("SNAPSHOT") &&
                gradle.taskGraph.allTasks.any { it.name.startsWith("publish") }
    }

    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
