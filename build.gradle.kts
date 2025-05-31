val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val postgresql_driver_version: String by project

plugins {
    kotlin("jvm") version "1.9.22" // Версия из gradle.properties
    id("io.ktor.plugin") version "2.3.10" // Версия Ktor плагина, должна соответствовать ktor_version
    kotlin("plugin.serialization") version "1.9.22" // Версия из gradle.properties
}

group = "ru.getapp" // Твой group ID
version = "0.0.1"

application {
    mainClass.set("ru.getapp.ApplicationKt") // Убедись, что это правильный путь к твоему ApplicationKt
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version") // Для ответов сервера
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version") // Для ответов сервера

    // Ktor Client (НУЖЕН СЕРВЕРУ ДЛЯ ЗАПРОСОВ К FCM)
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version") // Движок для клиента
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktor_version") // Для отправки JSON клиентом
    // ktor-serialization-kotlinx-json-jvm уже есть выше, он общий и для клиента и для сервера
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version") // Для логирования клиентских запросов

    // Logback (логирование)
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // PostgreSQL JDBC Драйвер
    implementation("org.postgresql:postgresql:$postgresql_driver_version")

    // Exposed (Kotlin SQL Framework)
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")

    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0") // Используй актуальную версию
    implementation("com.google.http-client:google-http-client-gson:1.44.1") // Или другой http-client, например, jackson


    // jBCrypt для хеширования паролей
    implementation("org.mindrot:jbcrypt:0.4")

    // Тестирование
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}