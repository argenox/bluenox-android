plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

group = "com.argenox"
version = (findProperty("VERSION_NAME") as String?) ?: "0.2.53"

android {
    namespace = "com.argenox.bluenoxandroid"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = group.toString()
            artifactId = "bluenox-android"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
            artifact(javadocJar)
            pom {
                name.set("BlueNox Android")
                description.set("BlueNox BLE Android library")
                url.set("https://github.com/argenox/bluenox-android")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("argenox")
                        name.set("Argenox")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/argenox/bluenox-android.git")
                    developerConnection.set("scm:git:ssh://github.com:argenox/bluenox-android.git")
                    url.set("https://github.com/argenox/bluenox-android")
                }
            }
        }
    }
    repositories {
        maven {
            name = "mavenCentral"
            // Sonatype Central Portal OSSRH-compatible endpoints.
            val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = (findProperty("centralPortalUsername") as String?)
                    ?: System.getenv("CENTRAL_PORTAL_USERNAME")
                    ?: (findProperty("ossrhUsername") as String?)
                    ?: System.getenv("OSSRH_USERNAME")
                password = (findProperty("centralPortalPassword") as String?)
                    ?: System.getenv("CENTRAL_PORTAL_PASSWORD")
                    ?: (findProperty("ossrhPassword") as String?)
                    ?: System.getenv("OSSRH_PASSWORD")
            }
        }
        mavenLocal()
    }
}

signing {
    val signingKey = (findProperty("signingInMemoryKey") as String?) ?: System.getenv("GPG_SIGNING_KEY")
    val signingPassword = (findProperty("signingInMemoryKeyPassword") as String?) ?: System.getenv("GPG_PASSWORD")
    if (!signingKey.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
