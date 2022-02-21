package tornaco.project.android.thanox

import org.gradle.internal.os.OperatingSystem
import java.io.File
import java.io.FileNotFoundException

fun buildTools(name: String): String {
    val sdkDir = sdkDir()

    val buildToolsDir = if (OperatingSystem.current().isWindows) {
        "$sdkDir\\build-tools\\"
    } else {
        "$sdkDir/build-tools/"
    }

    val preferredAidlFile = if (OperatingSystem.current().isWindows) {
        buildToolsDir + Configs.buildToolsVersion + "\\${name}.exe"
    } else {
        buildToolsDir + Configs.buildToolsVersion + "/${name}"
    }

    if (File(preferredAidlFile).exists()) {
        return preferredAidlFile
    }

    val latestBuildTools =
        File(buildToolsDir).listFiles()?.maxByOrNull { it.name.replace(".", "").toInt() }
            ?: throw FileNotFoundException("Can not find any build tools under: $buildToolsDir")

    return if (OperatingSystem.current().isWindows) {
        latestBuildTools.absolutePath + "\\${name}.exe"
    } else {
        latestBuildTools.absolutePath + "/${name}"
    }
}

private fun sdkDir(): String {
    return Configs["sdk.dir"] ?: System.getenv("ANDROID_HOME")
}