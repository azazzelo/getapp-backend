package ru.getapp

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ReferenceOption // Этот импорт уже должен быть

object UsersTable : Table("users") {
    val login = varchar("login", 50)
    val password = varchar("password", 255)
    val name = varchar("name", 50)
    val specialties = text("specialties").nullable()
    val bio = text("bio").nullable()
    val role = varchar("role", 20)
    override val primaryKey = PrimaryKey(login)
}

object SlotsTable : LongIdTable("slots") { // Убрали "id" как второй параметр, Exposed сам создаст колонку "id"
    // val id - уже неявно определена из LongIdTable
    val trainerLogin = varchar("trainer_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE) // ИСПРАВЛЕНИЕ
    val description = text("description")
    val slotDate = date("slot_date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val quantity = integer("quantity")
    // PrimaryKey (id) уже определен в LongIdTable
}

object SlotsClientsTable : Table("slots_clients") {
    val slotId = long("slot_id")
        .references(SlotsTable.id, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE) // ИСПРАВЛЕНИЕ
    val clientLogin = varchar("client_login", 50)
        .references(UsersTable.login, onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE) // ИСПРАВЛЕНИЕ
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
            SchemaUtils.createMissingTablesAndColumns(UsersTable, SlotsTable, SlotsClientsTable)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}