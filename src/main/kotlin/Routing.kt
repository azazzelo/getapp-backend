package ru.getapp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.mindrot.jbcrypt.BCrypt // Для хеширования и проверки паролей
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun ApplicationCall.parseTimeFlexible(timeStr: String, inputTimeFormatters: List<DateTimeFormatter>): LocalTime? {
    inputTimeFormatters.forEach { formatter ->
        try {
            return LocalTime.parse(timeStr, formatter)
        } catch (e: DateTimeParseException) { /* Попробовать следующий формат */ }
    }
    application.log.warn("Could not parse time: $timeStr")
    return null
}

fun Application.configureRouting() {
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val inputTimeFormattersForParsing = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("HH:mm:ss")
    )
    val dbTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    routing {
        get("/") {
            call.respondText("GetApp Ktor Server is running!")
        }

        // --- АУТЕНТИФИКАЦИЯ ---
        post("/login") {
            try {
                val loginRequest = call.receive<LoginRequest>()
                val userRecord = DatabaseFactory.dbQuery {
                    UsersTable.select { UsersTable.login eq loginRequest.login }.singleOrNull()
                }

                if (userRecord != null) {
                    val storedHashedPassword = userRecord[UsersTable.password]
                    if (BCrypt.checkpw(loginRequest.password, storedHashedPassword)) {
                        val apiResponse = UserApiResponse(
                            login = userRecord[UsersTable.login], name = userRecord[UsersTable.name],
                            role = userRecord[UsersTable.role], specialties = userRecord[UsersTable.specialties],
                            bio = userRecord[UsersTable.bio]
                        )
                        call.respond(HttpStatusCode.OK, apiResponse)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid login or password"))
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Invalid login or password"))
                }
            } catch (e: ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
            } catch (e: Exception) {
                application.log.error("Login endpoint error", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Server error: ${e.localizedMessage}"))
            }
        }

        // --- ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ ---
        get("/users/{login}") {
            val userLogin = call.parameters["login"]
            if (userLogin.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("User login parameter is required")); return@get }
            try {
                val userRecord = DatabaseFactory.dbQuery { UsersTable.select { UsersTable.login eq userLogin }.singleOrNull() }
                if (userRecord != null) {
                    call.respond(HttpStatusCode.OK, UserApiResponse(login = userRecord[UsersTable.login], name = userRecord[UsersTable.name], role = userRecord[UsersTable.role], specialties = userRecord[UsersTable.specialties], bio = userRecord[UsersTable.bio]))
                } else { call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found")) }
            } catch (e: Exception) { application.log.error("Get user profile error for $userLogin", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error fetching user profile: ${e.localizedMessage}")) }
        }

        put("/users/{login}") {
            val userLogin = call.parameters["login"]; if (userLogin.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("User login parameter is required")); return@put }
            try {
                val request = call.receive<UserProfileUpdateRequest>()
                val updatedRows = DatabaseFactory.dbQuery {
                    UsersTable.update({ UsersTable.login eq userLogin }) { stmt ->
                        stmt[name] = request.name
                        stmt[bio] = request.bio
                        val userRole = UsersTable.select{ UsersTable.login eq userLogin}.singleOrNull()?.get(UsersTable.role)
                        if (userRole == "trainer") { stmt[specialties] = request.specialties }
                    }
                }
                if (updatedRows > 0) { call.respond(HttpStatusCode.OK, SimpleMessageResponse("Profile updated successfully")) }
                else { call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found or no changes made")) }
            } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
            } catch (e: Exception) { application.log.error("Update user profile error for $userLogin", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error updating profile: ${e.localizedMessage}")) }
        }

        // --- СПИСОК ТРЕНЕРОВ ---
        get("/users/role/trainer") {
            try {
                val trainers = DatabaseFactory.dbQuery { UsersTable.select { UsersTable.role eq "trainer" }.map { UserApiResponse(login = it[UsersTable.login], name = it[UsersTable.name], role = it[UsersTable.role], specialties = it[UsersTable.specialties], bio = it[UsersTable.bio]) } }
                call.respond(HttpStatusCode.OK, trainers)
            } catch (e: Exception) { application.log.error("Error fetching trainers", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error fetching trainers: ${e.localizedMessage}")) }
        }

        // --- ОПЕРАЦИИ СО СЛОТАМИ (КЛИЕНТ/ТРЕНЕР) ---
        get("/slots/available/{clientLogin}") {
            val clientLogin = call.parameters["clientLogin"]; if (clientLogin.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Client login parameter is required")); return@get }
            try {
                val bookedSlotIds = DatabaseFactory.dbQuery { SlotsClientsTable.select { SlotsClientsTable.clientLogin eq clientLogin }.map { it[SlotsClientsTable.slotId] } }
                val availableSlots = DatabaseFactory.dbQuery {
                    SlotsTable.innerJoin(UsersTable, { SlotsTable.trainerLogin }, { UsersTable.login })
                        .slice(SlotsTable.columns + UsersTable.name)
                        .select { (SlotsTable.quantity greater 0) and (SlotsTable.id notInList bookedSlotIds) }
                        .orderBy(SlotsTable.slotDate to SortOrder.ASC, SlotsTable.startTime to SortOrder.ASC)
                        .map { SlotApiResponse(id = it[SlotsTable.id].value, trainerLogin = it[SlotsTable.trainerLogin], description = it[SlotsTable.description], slotDate = it[SlotsTable.slotDate].format(dateFormatter), startTime = it[SlotsTable.startTime].format(dbTimeFormatter), endTime = it[SlotsTable.endTime].format(dbTimeFormatter), quantity = it[SlotsTable.quantity], trainerName = it[UsersTable.name]) }
                }
                call.respond(HttpStatusCode.OK, availableSlots)
            } catch (e: Exception) { application.log.error("Error fetching available slots for $clientLogin", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error fetching available slots: ${e.localizedMessage}")) }
        }

        get("/slots/trainer/{trainerLogin}") {
            val trainerLogin = call.parameters["trainerLogin"]; if (trainerLogin.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Trainer login parameter is required")); return@get }
            try {
                val trainerSlots = DatabaseFactory.dbQuery {
                    SlotsTable.select { SlotsTable.trainerLogin eq trainerLogin }
                        .orderBy(SlotsTable.slotDate to SortOrder.DESC, SlotsTable.startTime to SortOrder.ASC)
                        .map { SlotApiResponse(id = it[SlotsTable.id].value, trainerLogin = it[SlotsTable.trainerLogin], description = it[SlotsTable.description], slotDate = it[SlotsTable.slotDate].format(dateFormatter), startTime = it[SlotsTable.startTime].format(dbTimeFormatter), endTime = it[SlotsTable.endTime].format(dbTimeFormatter), quantity = it[SlotsTable.quantity]) }
                }
                call.respond(HttpStatusCode.OK, trainerSlots)
            } catch (e: Exception) { application.log.error("Error fetching slots for trainer $trainerLogin", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error fetching trainer slots: ${e.localizedMessage}")) }
        }

        post("/slots") { // Создание слота (тренером или админом, если указан trainerLogin)
            try {
                val request = call.receive<CreateSlotRequest>()
                val trainer = DatabaseFactory.dbQuery { UsersTable.select { (UsersTable.login eq request.trainerLogin) and (UsersTable.role eq "trainer") }.singleOrNull() }
                if (trainer == null) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Trainer with login ${request.trainerLogin} not found or is not a trainer.")); return@post }
                val parsedDate = try { LocalDate.parse(request.slotDate, dateFormatter) } catch (e: DateTimeParseException) { null }
                val parsedStartTime = call.parseTimeFlexible(request.startTime, inputTimeFormattersForParsing)
                val parsedEndTime = call.parseTimeFlexible(request.endTime, inputTimeFormattersForParsing)
                if (parsedDate == null || parsedStartTime == null || parsedEndTime == null) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid date or time format. Use yyyy-MM-dd and HH:mm or HH:mm:ss")); return@post }
                if (parsedEndTime.isBefore(parsedStartTime) || parsedEndTime == parsedStartTime) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("End time must be after start time")); return@post }
                val newSlotIdEntity = DatabaseFactory.dbQuery { SlotsTable.insertAndGetId { it[this.trainerLogin] = request.trainerLogin; it[description] = request.description; it[slotDate] = parsedDate; it[startTime] = parsedStartTime; it[endTime] = parsedEndTime; it[quantity] = request.quantity } }
                call.respond(HttpStatusCode.Created, CreateResponse("Slot created successfully", newSlotIdEntity.value))
            } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
            } catch (e: Exception) { application.log.error("Create slot error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error creating slot: ${e.localizedMessage}")) }
        }

        put("/slots/{slotId}") { // Обновление слота
            val slotIdParam = call.parameters["slotId"]?.toLongOrNull(); if (slotIdParam == null) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid Slot ID")); return@put }
            try {
                val request = call.receive<UpdateSlotRequest>()
                // Здесь можно добавить проверку, что слот принадлежит текущему тренеру (если это не админ)
                val updatedRows = DatabaseFactory.dbQuery { SlotsTable.update({ SlotsTable.id eq slotIdParam }) { stmt -> request.description?.let { stmt[description] = it }; request.slotDate?.let { parsed -> LocalDate.parse(parsed, dateFormatter).let { stmt[slotDate] = it } }; request.startTime?.let { timeStr -> call.parseTimeFlexible(timeStr, inputTimeFormattersForParsing)?.let { stmt[startTime] = it } ?: throw DateTimeParseException("Invalid start time format", timeStr, 0) }; request.endTime?.let { timeStr -> call.parseTimeFlexible(timeStr, inputTimeFormattersForParsing)?.let { stmt[endTime] = it } ?: throw DateTimeParseException("Invalid end time format", timeStr, 0) }; request.quantity?.let { stmt[quantity] = it } } }
                if (updatedRows > 0) { call.respond(HttpStatusCode.OK, SimpleMessageResponse("Slot updated successfully")) } else { call.respond(HttpStatusCode.NotFound, ErrorResponse("Slot not found or no changes made")) }
            } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
            } catch (e: DateTimeParseException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid date/time format: ${e.message}"))
            } catch (e: Exception) { application.log.error("Update slot error for ID $slotIdParam", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error updating slot: ${e.localizedMessage}")) }
        }

        delete("/slots/{slotId}") { // Удаление слота
            val slotIdParam = call.parameters["slotId"]?.toLongOrNull(); if (slotIdParam == null) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid Slot ID")); return@delete }
            try {
                // Здесь можно добавить проверку, что слот принадлежит текущему тренеру (если это не админ)
                val deletedRows = DatabaseFactory.dbQuery {
                    SlotsTable.deleteWhere { SlotsTable.id eq slotIdParam }
                }
                if (deletedRows > 0) {
                    call.respond(HttpStatusCode.OK, SimpleMessageResponse("Slot deleted successfully"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Slot not found"))
                }
            } catch (e: Exception) {
                application.log.error("Delete slot error for ID $slotIdParam", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error deleting slot: ${e.localizedMessage}"))
            }
        }

        // --- ЗАПИСИ КЛИЕНТОВ НА СЛОТЫ ---
        post("/slots/{slotId}/book") {
            val slotIdParam = call.parameters["slotId"]?.toLongOrNull(); if (slotIdParam == null) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid Slot ID")); return@post }
            try {
                val request = call.receive<SlotBookingClientRequest>()
                val clientExists = DatabaseFactory.dbQuery { UsersTable.select { UsersTable.login eq request.clientLogin and (UsersTable.role eq "client") }.count() > 0 }
                if (!clientExists) { call.respond(HttpStatusCode.NotFound, ErrorResponse("Client with login ${request.clientLogin} not found or is not a client")); return@post }
                val result: Result<BookingConfirmationResponse> = DatabaseFactory.dbQuery { val slot = SlotsTable.select { SlotsTable.id eq slotIdParam }.singleOrNull() ?: return@dbQuery Result.failure(NoSuchElementException("Slot not found")); if (slot[SlotsTable.quantity] <= 0) { return@dbQuery Result.failure(IllegalStateException("No places available in slot")) }; val alreadyBooked = SlotsClientsTable.select { (SlotsClientsTable.slotId eq slotIdParam) and (SlotsClientsTable.clientLogin eq request.clientLogin) }.count() > 0; if (alreadyBooked) { return@dbQuery Result.failure(IllegalStateException("Client already booked for this slot")) }; SlotsClientsTable.insert { it[this.slotId] = slotIdParam; it[this.clientLogin] = request.clientLogin }; SlotsTable.update({ SlotsTable.id eq slotIdParam }) { it[quantity] = slot[SlotsTable.quantity] - 1 }; Result.success(BookingConfirmationResponse("Successfully booked slot", slotIdParam, request.clientLogin)) }
                result.fold( onSuccess = { call.respond(HttpStatusCode.Created, it) }, onFailure = { e -> when(e) { is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Not found")); is IllegalStateException -> call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Conflict")); else -> throw e } } )
            } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
            } catch (e: Exception) { application.log.error("Book slot $slotIdParam error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error booking slot: ${e.localizedMessage}")) }
        }
        get("/clients/{clientLogin}/bookings") {
            val clientLoginParam = call.parameters["clientLogin"]; if (clientLoginParam.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Client login parameter is required")); return@get }
            try {
                val bookedSlots = DatabaseFactory.dbQuery { SlotsClientsTable.innerJoin(SlotsTable, { SlotsClientsTable.slotId }, { SlotsTable.id }).innerJoin(UsersTable, { SlotsTable.trainerLogin }, { UsersTable.login }).slice(SlotsTable.columns + UsersTable.name).select { SlotsClientsTable.clientLogin eq clientLoginParam }.orderBy(SlotsTable.slotDate to SortOrder.ASC, SlotsTable.startTime to SortOrder.ASC).map { SlotApiResponse(id = it[SlotsTable.id].value, trainerLogin = it[SlotsTable.trainerLogin], description = it[SlotsTable.description], slotDate = it[SlotsTable.slotDate].format(dateFormatter), startTime = it[SlotsTable.startTime].format(dbTimeFormatter), endTime = it[SlotsTable.endTime].format(dbTimeFormatter), quantity = it[SlotsTable.quantity], trainerName = it[UsersTable.name]) } }
                call.respond(HttpStatusCode.OK, bookedSlots)
            } catch (e: Exception) { application.log.error("Error fetching bookings for client $clientLoginParam", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error fetching bookings: ${e.localizedMessage}")) }
        }
        delete("/slots/{slotId}/book/{clientLogin}") {
            val slotIdParam = call.parameters["slotId"]?.toLongOrNull(); val clientLoginParam = call.parameters["clientLogin"]; if (slotIdParam == null || clientLoginParam.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Slot ID and Client Login are required")); return@delete }
            try {
                val result: Result<BookingConfirmationResponse> = DatabaseFactory.dbQuery { val bookingExists = SlotsClientsTable.select { (SlotsClientsTable.slotId eq slotIdParam) and (SlotsClientsTable.clientLogin eq clientLoginParam) }.count() > 0; if (!bookingExists) { return@dbQuery Result.failure(NoSuchElementException("Booking not found for this client and slot")) }; SlotsClientsTable.deleteWhere { (this.slotId eq slotIdParam) and (this.clientLogin eq clientLoginParam) }; val slot = SlotsTable.select { SlotsTable.id eq slotIdParam }.singleOrNull(); if (slot != null) { SlotsTable.update({ SlotsTable.id eq slotIdParam }) { it[quantity] = slot[SlotsTable.quantity] + 1 } }; Result.success(BookingConfirmationResponse("Booking cancelled successfully", slotIdParam, clientLoginParam)) }
                result.fold( onSuccess = { call.respond(HttpStatusCode.OK, it) }, onFailure = { e -> when(e) { is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Not found")); else -> throw e } } )
            } catch (e: Exception) { application.log.error("Cancel booking error for slot $slotIdParam, client $clientLoginParam", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error cancelling booking: ${e.localizedMessage}")) }
        }

        // УБРАН ЭНДПОИНТ: post("/users/{login}/fcm-token")

        // --- АДМИНСКИЕ ЭНДПОИНТЫ ---
        route("/admin") {
            get("/users") {
                try {
                    val users = DatabaseFactory.dbQuery {
                        UsersTable.selectAll().orderBy(UsersTable.login to SortOrder.ASC)
                            .map { UserApiResponse(it[UsersTable.login], it[UsersTable.name], it[UsersTable.role], it[UsersTable.specialties], it[UsersTable.bio]) }
                    }
                    call.respond(users)
                } catch (e: Exception) { application.log.error("Admin get users error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch users: ${e.localizedMessage}")) }
            }
            post("/users") {
                try {
                    val request = call.receive<AdminCreateUserRequest>()
                    val existingUser = DatabaseFactory.dbQuery { UsersTable.select { UsersTable.login eq request.login }.count() > 0 }
                    if (existingUser) { call.respond(HttpStatusCode.Conflict, ErrorResponse("User with login ${request.login} already exists")); return@post }
                    val hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt())
                    DatabaseFactory.dbQuery {
                        UsersTable.insert {
                            it[login] = request.login; it[password] = hashedPassword; it[name] = request.name; it[role] = request.role
                            if (request.role == "trainer") { it[specialties] = request.specialties } else { it[specialties] = null }
                            it[bio] = request.bio
                        }
                    }
                    call.respond(HttpStatusCode.Created, SimpleMessageResponse("User ${request.login} created successfully"))
                } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
                } catch (e: Exception) { application.log.error("Admin create user error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create user: ${e.localizedMessage}")) }
            }
            put("/users/{login}") {
                val userLogin = call.parameters["login"]
                if (userLogin.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("User login parameter is required")); return@put }
                try {
                    val request = call.receive<AdminUpdateUserRequest>()
                    val updatedRows = DatabaseFactory.dbQuery {
                        UsersTable.update({ UsersTable.login eq userLogin }) { stmt ->
                            request.name?.let { stmt[name] = it }; request.role?.let { stmt[role] = it }
                            val finalRole = request.role ?: UsersTable.select { UsersTable.login eq userLogin }.singleOrNull()?.get(UsersTable.role)
                            if (finalRole == "trainer") { stmt[specialties] = request.specialties } else { stmt[specialties] = null }
                            request.bio?.let { stmt[bio] = it }; request.password?.let { newPassword -> if (newPassword.isNotBlank()) stmt[password] = BCrypt.hashpw(newPassword, BCrypt.gensalt()) }
                        }
                    }
                    if (updatedRows > 0) call.respond(HttpStatusCode.OK, SimpleMessageResponse("User updated successfully"))
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found or no changes made"))
                } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
                } catch (e: Exception) { application.log.error("Admin update user error for $userLogin", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to update user: ${e.localizedMessage}")) }
            }
            delete("/users/{login}") {
                val userLogin = call.parameters["login"]; if (userLogin.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("User login parameter is required")); return@delete }
                try {
                    val deletedRows = DatabaseFactory.dbQuery { UsersTable.deleteWhere { UsersTable.login eq userLogin } }
                    if (deletedRows > 0) call.respond(HttpStatusCode.OK, SimpleMessageResponse("User deleted successfully"))
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                } catch (e: Exception) {
                    application.log.error("Admin delete user error for $userLogin", e)
                    if (e.cause is org.postgresql.util.PSQLException && (e.cause as org.postgresql.util.PSQLException).sqlState == "23503") {
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("Cannot delete user. User is referenced in slots or bookings."))
                    } else { call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete user: ${e.localizedMessage}")) }
                }
            }
            get("/slots") {
                try {
                    val allSlots = DatabaseFactory.dbQuery {
                        SlotsTable.innerJoin(UsersTable, { SlotsTable.trainerLogin }, { UsersTable.login })
                            .slice(SlotsTable.columns + UsersTable.name)
                            .selectAll()
                            .orderBy(SlotsTable.slotDate to SortOrder.DESC, SlotsTable.startTime to SortOrder.ASC)
                            .map { SlotApiResponse(id = it[SlotsTable.id].value, trainerLogin = it[SlotsTable.trainerLogin], description = it[SlotsTable.description], slotDate = it[SlotsTable.slotDate].format(dateFormatter), startTime = it[SlotsTable.startTime].format(dbTimeFormatter), endTime = it[SlotsTable.endTime].format(dbTimeFormatter), quantity = it[SlotsTable.quantity], trainerName = it[UsersTable.name]) }
                    }
                    call.respond(allSlots)
                } catch (e: Exception) { application.log.error("Admin get all slots error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch all slots: ${e.localizedMessage}")) }
            }
            get("/slots-clients") {
                try {
                    val entries = DatabaseFactory.dbQuery { SlotsClientsTable.selectAll().orderBy(SlotsClientsTable.slotId to SortOrder.ASC).map { SlotClientEntry(it[SlotsClientsTable.slotId], it[SlotsClientsTable.clientLogin]) } }
                    call.respond(entries)
                } catch (e: Exception) { application.log.error("Admin get slots-clients error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to fetch slot-client entries: ${e.localizedMessage}")) }
            }
            post("/slots-clients") {
                try {
                    val request = call.receive<AdminSlotClientRequest>()
                    val clientExists = DatabaseFactory.dbQuery { UsersTable.select { UsersTable.login eq request.clientLogin and (UsersTable.role eq "client") }.count() > 0 }
                    if (!clientExists) { call.respond(HttpStatusCode.NotFound, ErrorResponse("Client not found or is not a client")); return@post }
                    val slotResult = DatabaseFactory.dbQuery { SlotsTable.select { SlotsTable.id eq request.slotId }.singleOrNull() }
                    if (slotResult == null) { call.respond(HttpStatusCode.NotFound, ErrorResponse("Slot not found")); return@post }
                    if (slotResult[SlotsTable.quantity] <= 0) { call.respond(HttpStatusCode.Conflict, ErrorResponse("No places available in slot")); return@post }
                    val alreadyBooked = DatabaseFactory.dbQuery { SlotsClientsTable.select { (SlotsClientsTable.slotId eq request.slotId) and (SlotsClientsTable.clientLogin eq request.clientLogin) }.count() > 0 }
                    if (alreadyBooked) { call.respond(HttpStatusCode.Conflict, ErrorResponse("Client already booked for this slot")); return@post }
                    DatabaseFactory.dbQuery { SlotsClientsTable.insert { it[slotId] = request.slotId; it[clientLogin] = request.clientLogin }; SlotsTable.update({ SlotsTable.id eq request.slotId }) { it[quantity] = slotResult[SlotsTable.quantity] - 1 } }
                    call.respond(HttpStatusCode.Created, SimpleMessageResponse("Booking created by admin"))
                } catch (e: ContentTransformationException) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request body: ${e.message}"))
                } catch (e: Exception) { application.log.error("Admin create slot-client error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to create booking: ${e.localizedMessage}")) }
            }
            delete("/slots-clients/{slotId}/{clientLogin}") {
                val slotIdParam = call.parameters["slotId"]?.toLongOrNull(); val clientLoginParam = call.parameters["clientLogin"]; if (slotIdParam == null || clientLoginParam.isNullOrBlank()) { call.respond(HttpStatusCode.BadRequest, ErrorResponse("Slot ID and Client Login are required")); return@delete }
                try {
                    val slotResult = DatabaseFactory.dbQuery { SlotsTable.select { SlotsTable.id eq slotIdParam }.singleOrNull() }
                    val deletedRows = DatabaseFactory.dbQuery { SlotsClientsTable.deleteWhere { (this.slotId eq slotIdParam) and (this.clientLogin eq clientLoginParam) } }
                    if (deletedRows > 0 && slotResult != null) { DatabaseFactory.dbQuery { SlotsTable.update({ SlotsTable.id eq slotIdParam }) { it[quantity] = slotResult[SlotsTable.quantity] + 1 } }; call.respond(HttpStatusCode.OK, SimpleMessageResponse("Booking deleted by admin"))
                    } else if (deletedRows == 0) { call.respond(HttpStatusCode.NotFound, ErrorResponse("Booking not found"))
                    } else { call.respond(HttpStatusCode.OK, SimpleMessageResponse("Booking deleted, slot not found to update quantity (or already updated)")) }
                } catch (e: Exception) { application.log.error("Admin delete slot-client error", e); call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to delete booking: ${e.localizedMessage}")) }
            }
        }
    }
}