plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "com.argenox"
version = "0.2.52"

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
            pom {
                name.set("BlueNox Android")
                description.set("BlueNox BLE Android library")
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
