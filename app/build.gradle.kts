plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val updateNetHackResources by tasks.registering(Exec::class) {
    group = "nethack"
    description = "Build and copy NetHack resource files before Android builds."

    val nethackDir = layout.projectDirectory.dir("src/main/cpp/NetHack")
    val luaSourceDir = layout.projectDirectory.dir("src/main/cpp/Lua/lua")
    val luaSourcePath = luaSourceDir.asFile.absolutePath.replace('\\', '/')
    val cppDir = layout.projectDirectory.dir("src/main/cpp")
    val assetsDir = layout.projectDirectory.dir("src/main/assets")

    workingDir = nethackDir.asFile
    commandLine(
        "sh",
        "-c",
        """
        set -eu

        lua_src="$luaSourcePath"
        lua_compat_dir="lib/lua-5.4.8/src"
        if [ ! -f "${'$'}lua_src/lua.h" ]; then
            echo "Lua submodule is missing: ${'$'}lua_src/lua.h" >&2
            exit 1
        fi
        if [ ! -f "${'$'}lua_compat_dir/lua.h" ]; then
            mkdir -p lib/lua-5.4.8
            rm -rf "${'$'}lua_compat_dir"
            if ! ln -s "${'$'}lua_src" "${'$'}lua_compat_dir" 2>/dev/null; then
                mkdir -p "${'$'}lua_compat_dir"
                cp -R "${'$'}lua_src"/. "${'$'}lua_compat_dir"/
            fi
        fi

        if [ ! -f Makefile ] || [ ! -f dat/Makefile ] || [ ! -f util/Makefile ] || [ ! -f src/Makefile ]; then
            (cd sys/unix && ./setup.sh hints/linux-minimal)
        fi

        make -C dat all options
        make dlb
        make -C util ../src/tile.c
        make -C dat nhtiles.bmp

        mkdir -p "${assetsDir.asFile.absolutePath}/nethackdir" "${assetsDir.asFile.absolutePath}/tiles"

        copy_if_changed() {
            src="${'$'}1"
            dst="${'$'}2"
            if [ ! -f "${'$'}dst" ] || ! cmp -s "${'$'}src" "${'$'}dst"; then
                cp "${'$'}src" "${'$'}dst"
            fi
        }

        copy_if_changed dat/nhdat "${assetsDir.asFile.absolutePath}/nethackdir/nhdat"
        copy_if_changed src/tile.c "${cppDir.asFile.absolutePath}/tile.c"
        copy_if_changed dat/nhtiles.bmp "${assetsDir.asFile.absolutePath}/tiles/default_tiles_16.bmp"
        """.trimIndent()
    )
}

android {
    namespace = "com.yywspace.anethack"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yywspace.anethack.cn"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "3.7.0.a2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    applicationVariants.all {
        outputs.all {
            val ver = defaultConfig.versionName
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "ANetHack-$ver.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
