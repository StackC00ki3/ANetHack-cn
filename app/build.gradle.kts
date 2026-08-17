plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun secret(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

val updateNetHackResources by tasks.registering(Exec::class) {
    group = "nethack"
    description = "Build and copy NetHack resource files before Android builds."

    val nethackDir = layout.projectDirectory.dir("src/main/cpp/NetHack")
    val luaSourceDir = layout.projectDirectory.dir("src/main/cpp/Lua/lua")
    val cppDir = layout.projectDirectory.dir("src/main/cpp")
    val assetsDir = layout.projectDirectory.dir("src/main/assets")
    val resourceScript = layout.projectDirectory.file("scripts/update-nethack-resources.sh")
    fun unixPath(path: String) = path.replace('\\', '/')

    workingDir = layout.projectDirectory.asFile
    val scriptArgs = listOf(
        unixPath(resourceScript.asFile.absolutePath),
        unixPath(nethackDir.asFile.absolutePath),
        unixPath(luaSourceDir.asFile.absolutePath),
        unixPath(cppDir.asFile.absolutePath),
        unixPath(assetsDir.asFile.absolutePath)
    )
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        fun wslPath(path: String): String {
            val match = Regex("^([A-Za-z]):/(.*)$").matchEntire(path)
            return if (match == null) path else
                "/mnt/${match.groupValues[1].lowercase()}/${match.groupValues[2]}"
        }
        commandLine(listOf("wsl.exe", "--exec", "sh") + scriptArgs.map(::wslPath))
    } else {
        commandLine(listOf("sh") + scriptArgs)
    }
}

android {
    namespace = "com.yywspace.anethack"
    compileSdk = 34

    val releaseStoreFile = secret("ANDROID_KEYSTORE_FILE")
    val releaseStorePassword = secret("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = secret("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = secret("ANDROID_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    defaultConfig {
        applicationId = "com.yywspace.anethack.cn"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "5.0.0.a2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        outputs.all {
            val ver = defaultConfig.versionName
            // ABI filter name inlined: OutputFile/VariantOutput are deprecated and
            // the new Variant API offers no APK renaming hook, so keep the impl cast.
            val abi = (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .getFilter("ABI")
            outputFileName = "ANetHack-$ver${if (abi != null) "-$abi" else ""}.apk"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

tasks.named("preBuild") {
    dependsOn(updateNetHackResources)
}


dependencies {

    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.github.getActivity:XXPermissions:20.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
