// ============================================================================
// Phase 7 (tasks.md 8.7) — JaCoCo 覆盖率配置（按需启用）
// ============================================================================
// 启用方式（需有网络以解析 org.jacoco:org.jacoco.agent）：
//   ./gradlew -PenableJacoco testDebugUnitTest jacocoTestReport
//
// 报告输出：
//   app/build/reports/jacoco/jacocoTestReport/html/index.html  (HTML)
//   app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml (XML)
//
// 设计说明：
//   AGP 应用 jacoco plugin 后会自动为 testDebugUnitTest 配置 jacocoAgent 依赖。
//   离线环境无 org.jacoco:org.jacoco.agent 缓存会导致 testDebugUnitTest 失败，
//   故本脚本不默认 apply，仅在 -PenableJacoco 时由 app/build.gradle.kts 引入。
// ============================================================================

apply(plugin = "jacoco")

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates JaCoCo code coverage report for debug unit tests."

    val testTask = tasks.named("testDebugUnitTest", Test::class).get()
    testTask.extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = false
    }
    dependsOn(testTask)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val debugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/debug") {
        exclude(
            // Hilt / Dagger 生成代码（非业务逻辑，不计入覆盖率）
            "**/Hilt_*.*",
            "**/*_HiltComponents*.*",
            "**/*_GeneratedInjector*.*",
            "**/Dagger*.*",
            // Room 生成实现
            "**/*_Impl*.*",
            // Glide 生成
            "**/Glide*.*",
            "**/GeneratedRequestBuilder*.*",
            // Parcelize 生成
            "**/*Parcelizer*.*",
            // R8/构建生成
            "**/BuildConfig.*",
            "**/R.*",
            "**/R$*.*",
        )
    }

    classDirectories.setFrom(debugTree)
    sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
    executionData.setFrom(fileTree(project.buildDir).include("jacoco/*.exec"))
}
