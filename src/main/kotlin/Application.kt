package ru.getapp // Или твой актуальный group ID

// Убедись, что все эти импорты корректны для твоей структуры
// import com.example.db.DatabaseFactory // Если DatabaseFactory в том же пакете, этот импорт не нужен
// import com.example.plugins.configureRouting // Если Routing.kt в том же пакете, то просто configureRouting()
// import com.example.plugins.configureSerialization // Аналогично

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureSerialization() // Функция должна быть доступна (в том же пакете или импортирована)
    configureRouting()     // Функция должна быть доступна
}