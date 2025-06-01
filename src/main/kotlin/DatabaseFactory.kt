package ru.getapp

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object UsersTable : Table("users") {
    val login = varchar("login", 50)
    val password = varchar("password", 255)
    val name = varchar("name", 50)
    val specialties = text("specialties").nullable()
    val bio = text("bio").nullable()
    val role = varchar("role", 20)
    // fcmToken был убран
    override val primaryKey = PrimaryKey(login)
}

object SlotsTable : LongIdTable("slots") {
    val trainerLogin = varchar("trainer_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val description = text("description")
    val slotDate = date("slot_date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val quantity = integer("quantity")
}

object SlotsClientsTable : Table("slots_clients") {
    val slotId = long("slot_id").references(SlotsTable.id, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val clientLogin = varchar("client_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(slotId, clientLogin)
}

// НОВОЕ ОПРЕДЕЛЕНИЕ ТАБЛИЦЫ УВЕДОМЛЕНИЙ
object UserNotificationsTable : Table("user_notifications") {
    val id = long("id").autoIncrement() // Используем autoIncrement, т.к. не LongIdTable
    val userLogin = varchar("user_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val message = text("message")
    val isRead = bool("is_read").default(false)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestamp()) // Использует БД для генерации времени
    val relatedSlotId = long("related_slot_id")
        .references(SlotsTable.id, onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE)
        .nullable()
    override val primaryKey = PrimaryKey(id) // Явный первичный ключ для autoIncrement id
}

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = DatabaseConfig.DB_URL,
            driver = DatabaseConfig.DB_DRIVER,
            user = DatabaseConfig.DB_USER,
            password = DatabaseConfig.DB_PASSWORD
        )
        transaction {
            // ДОБАВЛЕНА UserNotificationsTable
            SchemaUtils.createMissingTablesAndColumns(UsersTable, SlotsTable, SlotsClientsTable, UserNotificationsTable)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}