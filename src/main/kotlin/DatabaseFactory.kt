package ru.getapp

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.LongIdTable // <<< УБЕДИСЬ, ЧТО ЭТОТ ИМПОРТ ЕСТЬ
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object UsersTable : Table("users") {
    val login = varchar("login", 50)
    val password = varchar("password", 255)
    val name = varchar("name", 50)
    val specialties = text("specialties").nullable()
    val bio = text("bio").nullable()
    val role = varchar("role", 20)
    override val primaryKey = PrimaryKey(login)
}

// ВАЖНО: SlotsTable наследуется от LongIdTable
object SlotsTable : LongIdTable("slots") { // "id" будет именем колонки по умолчанию

    val trainerLogin = varchar("trainer_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val description = text("description")
    val slotDate = date("slot_date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val quantity = integer("quantity")
    // PrimaryKey (id) уже определен в LongIdTable
}

object SlotsClientsTable : Table("slots_clients") {
    val slotId = long("slot_id").references(SlotsTable.id, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    val clientLogin = varchar("client_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(slotId, clientLogin)
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
            // Убери UserNotificationsTable, если она не используется
            SchemaUtils.createMissingTablesAndColumns(UsersTable, SlotsTable, SlotsClientsTable)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}