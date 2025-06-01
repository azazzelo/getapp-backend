package ru.getapp

object DatabaseConfig {
    const val DB_URL = "jdbc:postgresql://shuttle.proxy.rlwy.net:24902/railway?sslmode=require"

    val DB_HOST = System.getenv("PGHOST") ?: "shuttle.proxy.rlwy.net" // Твой хост как fallback
    val DB_PORT = System.getenv("PGPORT") ?: "24902"                  // Твой порт как fallback
    val DB_NAME = System.getenv("PGDATABASE") ?: "railway"            // Имя БД как fallback
    val DB_USER = System.getenv("PGUSER") ?: "postgres"               // Пользователь как fallback
    val DB_PASSWORD = System.getenv("PGPASSWORD") ?: "AqMnroKoFhpEbfCNAkrFpQttJWCpYXgB" // Твой пароль как fallback

    val JDBC_URL = "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?sslmode=require"
    const val DB_DRIVER = "org.postgresql.Driver"
}
