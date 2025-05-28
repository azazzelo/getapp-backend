package ru.getapp

object DatabaseConfig {
    const val DB_URL = "jdbc:postgresql://shuttle.proxy.rlwy.net:24902/railway?sslmode=require" // Твоя строка подключения из Railway
    const val DB_USER = "postgres" // Твой пользователь из Railway
    const val DB_PASSWORD = "AqMnroKoFhpEbfCNAkrFpQttJWCpYXgB" // Твой пароль из Railway
    const val DB_DRIVER = "org.postgresql.Driver"
}