import java.io.File
import java.io.RandomAccessFile

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.foresightlabs.aether.calls.media"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        ndkVersion = "27.2.12479018"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti", "-Wall")
                abiFilters += listOf("arm64-v8a")
                // Android 15 introduced devices with a 16 KB memory page size,
                // and a shared library whose LOAD segments are aligned to the
                // older 4 KB cannot be loaded on one. NDK r27 can produce the
                // larger alignment but does not do so unless asked; r28 makes
                // it the default, at which point this argument becomes a no-op
                // rather than something to remember to remove.
                //
                // Verify with:
                //   readelf -lW libcallmedia.so | awk '$1=="LOAD"{print $NF}'
                // which must report 0x4000, not 0x1000.
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Fails the build if a packaged native library would not load on a device with
 * a 16 KB memory page size.
 *
 * The alignment is a property of what the linker emitted, not of anything
 * visible in Kotlin, so no unit test can observe it -- but a toolchain change,
 * a dropped CMake argument, or a newly added library can silently reintroduce
 * 4 KB alignment, and the symptom is an app that installs and then fails to
 * start on hardware the developer may not have. Checking the actual ELF program
 * headers of the built artifact is the only honest guard.
 */
val verifyNativePageAlignment = tasks.register("verifyNativePageAlignment") {
    group = "verification"
    description = "Checks that packaged native libraries are aligned for 16 KB memory pages."
    val buildDir = layout.buildDirectory
    doLast {
        // Only the directories holding libraries that actually get packaged.
        // The CMake scratch tree under intermediates/cxx is deliberately not
        // scanned: abandoned configuration directories linger there after a
        // toolchain flag changes, and judging a release by a build artifact
        // nothing consumes would fail for a library that is not shipping.
        val packagedRoots = listOf(
            "intermediates/library_jni/release",
            "intermediates/library_and_local_jars_jni/release",
            "intermediates/stripped_native_libs/release",
            "intermediates/merged_native_libs/release"
        ).map { buildDir.get().asFile.resolve(it) }.filter { it.isDirectory }

        val libraries = packagedRoots
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "so" } }
            .toList()
        if (libraries.isEmpty()) {
            logger.lifecycle("No packaged native libraries to check for 16 KB page alignment.")
            return@doLast
        }
        val misaligned = libraries.filterNot { NativePageAlignment.isSixteenKilobyteAligned(it) }
        if (misaligned.isNotEmpty()) {
            throw GradleException(
                "These native libraries are not aligned for 16 KB memory pages and would fail " +
                    "to load on Android 15+ devices that use them: " +
                    misaligned.joinToString { it.name } +
                    ". Build them with -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON (NDK r27) or NDK r28+."
            )
        }
        logger.lifecycle("16 KB page alignment verified for ${libraries.size} release native binar${if (libraries.size == 1) "y" else "ies"}.")
    }
}

// Hooked to the tasks that stage the libraries for packaging rather than to
// assembleRelease: an app module consuming this library never runs the
// library's assemble task, so hooking that would leave bundleRelease -- the
// build that actually ships -- unchecked.
tasks.matching {
    it.name == "copyReleaseJniLibsProjectOnly" ||
        it.name == "copyReleaseJniLibsProjectAndLocalJars" ||
        it.name == "stripReleaseDebugSymbols"
}.configureEach { finalizedBy(verifyNativePageAlignment) }

/** Minimal ELF program-header reader: enough to read each LOAD segment's alignment. */
object NativePageAlignment {
    private const val REQUIRED_ALIGNMENT = 16384L

    fun isSixteenKilobyteAligned(library: File): Boolean {
        RandomAccessFile(library, "r").use { file ->
            val identity = ByteArray(16)
            file.readFully(identity)
            // 0x7F 'E' 'L' 'F', then class (2 = 64-bit) and endianness (1 = little).
            if (identity[0] != 0x7F.toByte() || identity[1] != 'E'.code.toByte()) return true
            if (identity[4].toInt() != 2 || identity[5].toInt() != 1) return true

            file.seek(32)
            val programHeaderOffset = file.readLongLe()
            file.seek(54)
            val programHeaderSize = file.readShortLe()
            val programHeaderCount = file.readShortLe()

            for (index in 0 until programHeaderCount) {
                val entry = programHeaderOffset + index.toLong() * programHeaderSize
                file.seek(entry)
                val type = file.readIntLe()
                if (type != 1) continue // PT_LOAD
                file.seek(entry + 48) // p_align
                if (file.readLongLe() < REQUIRED_ALIGNMENT) return false
            }
            return true
        }
    }

    private fun RandomAccessFile.readIntLe(): Int =
        (0 until 4).fold(0) { value, byteIndex -> value or ((read() and 0xFF) shl (8 * byteIndex)) }

    private fun RandomAccessFile.readShortLe(): Int =
        (read() and 0xFF) or ((read() and 0xFF) shl 8)

    private fun RandomAccessFile.readLongLe(): Long =
        (0 until 8).fold(0L) { value, byteIndex -> value or ((read().toLong() and 0xFF) shl (8 * byteIndex)) }
}
