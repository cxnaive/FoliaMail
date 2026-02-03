plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.user"
version = "1.0.1"

dependencies {
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")

    // NBT-API Plugin 作为外部依赖，不打包
    compileOnly("de.tr7zw:item-nbt-api-plugin:2.15.5")

    // XConomy 经济插件软依赖
    compileOnly("com.github.YiC200333:XConomyAPI:2.25.1")

    // 其他依赖打包进 JAR
    implementation("com.h2database:h2:2.3.232")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://jitpack.io")
    gradlePluginPortal()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("FoliaMail-${version}.jar")

    // 重定位依赖包（SLF4J不重定位）
    relocate("org.h2", "dev.user.mailsystem.libs.org.h2")
    relocate("com.zaxxer", "dev.user.mailsystem.libs.com.zaxxer")
    relocate("com.mysql", "dev.user.mailsystem.libs.com.mysql")
    // SLF4J不重定位，避免ServiceLoader问题

    // 排除不需要的文件
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/LICENSE*")
    exclude("LICENSE*")

    // 合并 META-INF/services 文件
    mergeServiceFiles()

    // 不过度精简
    minimize {
        exclude(dependency("com.h2database:h2:.*"))
        exclude(dependency("com.mysql:mysql-connector-j:.*"))
        exclude(dependency("org.slf4j:slf4j-simple:.*"))
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
