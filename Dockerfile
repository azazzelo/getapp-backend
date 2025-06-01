# Используем официальный образ Gradle с JDK 17 (Ktor 2.x хорошо работает с JDK 17)
# Если твой проект строго требует JDK 11, можно найти образ с ним, например, gradle:jdk11
FROM gradle:8.5-jdk17 AS builder
# Или FROM gradle:jdk17-jammy, если нужен более легковесный Linux

# Устанавливаем рабочую директорию
WORKDIR /app

# Копируем сначала файлы сборки для использования кэша Docker
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Загружаем зависимости (этот слой будет кэшироваться, если файлы сборки не меняются)
# Можно добавить --no-daemon, чтобы не запускать демона Gradle в контейнере сборки
RUN gradle dependencies --no-daemon || true

# Копируем остальной исходный код
COPY src ./src

# Собираем приложение, создавая дистрибутив с помощью shadowJar или installDist
# Если используешь плагин 'application', то 'installDist' предпочтительнее
# Если 'com.github.johnrengelman.shadow', то 'shadowJar'
# Убедимся, что gradlew имеет права на выполнение (если он есть в репозитории)
# Если gradlew нет, используем просто gradle
# RUN chmod +x ./gradlew && ./gradlew installDist -x test --no-daemon
RUN gradle installDist -x test --no-daemon

# --- Второй этап: создаем легковесный образ для запуска ---
# Используем образ JRE, так как для запуска нужен только он
FROM eclipse-temurin:17-jre-jammy
# Или FROM openjdk:17-jre-slim

WORKDIR /app

# Копируем собранный дистрибутив из предыдущего этапа (builder)
COPY --from=builder /app/build/install/getapp-backend/ ./
# Убедись, что "getapp-backend" здесь - это имя твоего rootProject.name из settings.gradle.kts

# Устанавливаем порт, который будет слушать приложение
EXPOSE 8080

# Команда для запуска приложения
# Скрипт запуска будет в подпапке bin
CMD ["./bin/getapp-backend"]