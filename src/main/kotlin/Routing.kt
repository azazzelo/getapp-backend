package ru.getapp

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Предполагается, что DatabaseFactory, UsersTable, SlotsTable, SlotsClientsTable
// и все модели из ServerDataModels.kt (LoginRequest, UserApiResponse, SlotApiResponse,
// CreateSlotRequest, SlotBookingRequest, BookingResponse, SlotClientEntry)
// находятся в этом же пакете ru.getapp и доступны без явных импортов их самих.

fun Application.configureRouting() {
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val inputTimeFormatters = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("HH:mm:ss")
    )
    val dbTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun parseTimeFlexible(timeStr: String): LocalTime? {
        for (formatter in inputTimeFormatters) {
            try {
                return LocalTime.parse(timeStr, formatter)
            } catch (e: DateTimeParseException) {
                // continue
            }
        }
        return null
    }

    routing {
        get("/") {
            call.respondText("GetApp Ktor Server is running!")
        }

        post("/login") {
            try {
                val loginRequest = call.receive<LoginRequest>()
                val userDbData = DatabaseFactory.dbQuery {
                    UsersTable.select { UsersTable.login eq loginRequest.login }
                        .map {
                            mapOf(
                                "login" to it[UsersTable.login],
                                "name" to it[UsersTable.name],
                                "role" to it[UsersTable.role],
                                "password" to it[UsersTable.password]
                            )
                        }
                        .singleOrNull()
                }

                if (userDbData != null) {
                    if (userDbData["password"] == loginRequest.password) { // НЕБЕЗОПАСНО!
                        val apiResponse = UserApiResponse(
                            login = userDbData["login"] as String,
                            name = userDbData["name"] as String,
                            role = userDbData["role"] as String
                        )
                        call.respond(HttpStatusCode.OK, apiResponse)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid password"))
                    }
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                }
            } catch (e: ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body: ${e.message}"))
            } catch (e: Exception) {
                application.log.error("Login endpoint error", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Server error: ${e.localizedMessage}"))
            }
        }

        get("/slots/available/{clientLogin}") {
            val clientLogin = call.parameters["clientLogin"]
            if (clientLogin.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Client login parameter is required"))
                return@get
            }
            try {
                val bookedSlotIds = DatabaseFactory.dbQuery {
                    SlotsClientsTable.select { SlotsClientsTable.clientLogin eq clientLogin }
                        .map { it[SlotsClientsTable.slotId] }
                }
                val availableSlots = DatabaseFactory.dbQuery {
                    val query = SlotsTable.innerJoin(UsersTable, { SlotsTable.trainerLogin }, { UsersTable.login })
                        .slice(
                            SlotsTable.id, SlotsTable.trainerLogin, SlotsTable.description,
                            SlotsTable.slotDate, SlotsTable.startTime, SlotsTable.endTime,
                            SlotsTable.quantity, UsersTable.name
                        )
                        .select { (SlotsTable.quantity greater 0) }
                    val finalQuery = if (bookedSlotIds.isNotEmpty()) {
                        query.andWhere { SlotsTable.id notInList bookedSlotIds }
                    } else {
                        query
                    }
                    finalQuery.orderBy(SlotsTable.slotDate to SortOrder.ASC, SlotsTable.startTime to SortOrder.ASC)
                        .map {
                            SlotApiResponse(
                                id = it[SlotsTable.id].value,
                                trainerLogin = it[SlotsTable.trainerLogin],
                                description = it[SlotsTable.description],
                                slotDate = it[SlotsTable.slotDate].format(dateFormatter),
                                startTime = it[SlotsTable.startTime].format(dbTimeFormatter),
                                endTime = it[SlotsTable.endTime].format(dbTimeFormatter),
                                quantity = it[SlotsTable.quantity],
                                trainerName = it[UsersTable.name]
                            )
                        }
                }
                call.respond(availableSlots)
            } catch (e: Exception) {
                application.log.error("Failed to get available slots for $clientLogin", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error fetching available slots: ${e.localizedMessage}"))
            }
        }

        get("/slots/trainer/{trainerLogin}") {
            val trainerLogin = call.parameters["trainerLogin"]
            if (trainerLogin.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Trainer login parameter is required"))
                return@get
            }
            try {
                val trainerSlots = DatabaseFactory.dbQuery {
                    SlotsTable.select { SlotsTable.trainerLogin eq trainerLogin }
                        .orderBy(SlotsTable.slotDate to SortOrder.DESC, SlotsTable.startTime to SortOrder.ASC)
                        .map {
                            SlotApiResponse(
                                id = it[SlotsTable.id].value,
                                trainerLogin = it[SlotsTable.trainerLogin],
                                description = it[SlotsTable.description],
                                slotDate = it[SlotsTable.slotDate].format(dateFormatter),
                                startTime = it[SlotsTable.startTime].format(dbTimeFormatter),
                                endTime = it[SlotsTable.endTime].format(dbTimeFormatter),
                                quantity = it[SlotsTable.quantity]
                            )
                        }
                }
                call.respond(trainerSlots)
            } catch (e: Exception) {
                application.log.error("Failed to get trainer slots for $trainerLogin", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error fetching trainer slots: ${e.localizedMessage}"))
            }
        }

        post("/slots") {
            try {
                val request = call.receive<CreateSlotRequest>()
                val trainer = DatabaseFactory.dbQuery {
                    UsersTable.select { (UsersTable.login eq request.trainerLogin) and (UsersTable.role eq "trainer") }
                        .singleOrNull()
                }
                if (trainer == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Trainer with login '${request.trainerLogin}' not found or is not a trainer."))
                    return@post
                }
                val slotDate = LocalDate.parse(request.slotDate, dateFormatter)
                val startTime = parseTimeFlexible(request.startTime)
                val endTime = parseTimeFlexible(request.endTime)
                if (startTime == null || endTime == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid time format. Use HH:mm or HH:mm:ss."))
                    return@post
                }
                if (!endTime.isAfter(startTime)) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "End time must be strictly after start time."))
                    return@post
                }
                if (request.quantity < 0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Quantity cannot be negative."))
                    return@post
                }
                val newSlotGeneratedIdObject = DatabaseFactory.dbQuery {
                    SlotsTable.insertAndGetId {
                        it[SlotsTable.trainerLogin] = request.trainerLogin
                        it[SlotsTable.description] = request.description
                        it[SlotsTable.slotDate] = slotDate
                        it[SlotsTable.startTime] = startTime
                        it[SlotsTable.endTime] = endTime
                        it[SlotsTable.quantity] = request.quantity
                    }
                }
                call.respond(HttpStatusCode.Created, mapOf("message" to "Slot created successfully", "id" to newSlotGeneratedIdObject.value))
            } catch (e: ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body: ${e.message}"))
            } catch (e: DateTimeParseException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid date/time format: ${e.message}"))
            } catch (e: Exception) {
                // application.log.error("Failed to create slot", e) // ВРЕМЕННО ЗАКОММЕНТИРОВАНО ИЗ-ЗА ОШИБКИ 195
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error creating slot: ${e.localizedMessage}"))
            }
        }

        post("/slots/{slotId}/book") {
            val slotIdParam = call.parameters["slotId"]?.toLongOrNull()
            if (slotIdParam == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid Slot ID parameter"))
                return@post
            }

            try {
                val request = call.receive<SlotBookingRequest>()

                val clientUser = DatabaseFactory.dbQuery {
                    UsersTable.select { (UsersTable.login eq request.clientLogin) and (UsersTable.role eq "client") }
                        .singleOrNull()
                }
                if (clientUser == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Client with login '${request.clientLogin}' not found or is not a client."))
                    return@post
                }

                val slotData = DatabaseFactory.dbQuery {
                    SlotsTable.select { (SlotsTable.id eq slotIdParam) }
                        .map { Triple(it[SlotsTable.id].value, it[SlotsTable.quantity], it[SlotsTable.trainerLogin]) }
                        .singleOrNull()
                }

                if (slotData == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Slot with ID '$slotIdParam' not found."))
                    return@post
                }

                val (actualSlotId, currentQuantity, _) = slotData

                if (currentQuantity <= 0) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "No places available for slot ID '$actualSlotId'."))
                    return@post
                }

                val existingBooking = DatabaseFactory.dbQuery {
                    SlotsClientsTable.select {
                        (SlotsClientsTable.slotId eq actualSlotId) and (SlotsClientsTable.clientLogin eq request.clientLogin)
                    }.count() > 0
                }
                if (existingBooking) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Client '${request.clientLogin}' is already booked for slot ID '$actualSlotId'."))
                    return@post
                }

                DatabaseFactory.dbQuery {
                    SlotsClientsTable.insert {
                        it[SlotsClientsTable.slotId] = actualSlotId
                        it[SlotsClientsTable.clientLogin] = request.clientLogin
                    }
                    SlotsTable.update({ SlotsTable.id eq actualSlotId }) {
                        it[quantity] = currentQuantity - 1
                    }
                }
                call.respond(HttpStatusCode.Created, BookingResponse("Successfully booked slot.", actualSlotId, request.clientLogin))

            } catch (e: ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request body: ${e.message}"))
            } catch (e: Exception) {
                application.log.error("Failed to book slot $slotIdParam for client", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error booking slot: ${e.localizedMessage}"))
            }
        }

        delete("/slots/{slotId}/book/{clientLogin}") {
            val slotIdParam = call.parameters["slotId"]?.toLongOrNull()
            val clientLoginParam = call.parameters["clientLogin"]

            if (slotIdParam == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid Slot ID parameter"))
                return@delete
            }
            if (clientLoginParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Client login parameter is required"))
                return@delete
            }

            try {
                val bookingExists = DatabaseFactory.dbQuery {
                    SlotsClientsTable.select {
                        (SlotsClientsTable.slotId eq slotIdParam) and (SlotsClientsTable.clientLogin eq clientLoginParam)
                    }.count() > 0
                }
                if (!bookingExists) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Booking not found for client '$clientLoginParam' on slot ID '$slotIdParam'."))
                    return@delete
                }

                val currentSlotQuantity = DatabaseFactory.dbQuery {
                    SlotsTable.select { SlotsTable.id eq slotIdParam }
                        .map { it[SlotsTable.quantity] }
                        .singleOrNull()
                }

                if (currentSlotQuantity == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Slot with ID '$slotIdParam' not found, cannot update quantity."))
                    return@delete
                }

                DatabaseFactory.dbQuery {
                    val deletedRows = SlotsClientsTable.deleteWhere {
                        (SlotsClientsTable.slotId eq slotIdParam) and (SlotsClientsTable.clientLogin eq clientLoginParam) // ИСПРАВЛЕНО
                    }
                    if (deletedRows > 0) {
                        SlotsTable.update({ SlotsTable.id eq slotIdParam }) {
                            it[quantity] = currentSlotQuantity + 1
                        }
                    } else {
                        throw IllegalStateException("Booking existed but was not deleted.")
                    }
                }
                call.respond(HttpStatusCode.OK, BookingResponse("Booking cancelled successfully.", slotIdParam, clientLoginParam))

            } catch (e: IllegalStateException){
                application.log.error("Error cancelling booking (state issue) for $slotIdParam, client $clientLoginParam", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error cancelling booking due to inconsistent state."))
            }
            catch (e: Exception) {
                application.log.error("Failed to cancel booking for slot $slotIdParam, client $clientLoginParam", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error cancelling booking: ${e.localizedMessage}"))
            }
        }

        get("/clients/{clientLogin}/bookings") {
            val clientLoginParam = call.parameters["clientLogin"]
            if (clientLoginParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Client login parameter is required"))
                return@get
            }
            try {
                val bookedSlotIds = DatabaseFactory.dbQuery {
                    SlotsClientsTable.select { SlotsClientsTable.clientLogin eq clientLoginParam }
                        .map { it[SlotsClientsTable.slotId] }
                }

                if (bookedSlotIds.isNotEmpty()) {
                    val bookedSlotsDetails = DatabaseFactory.dbQuery {
                        SlotsTable.innerJoin(UsersTable, { SlotsTable.trainerLogin }, { UsersTable.login })
                            .slice(
                                SlotsTable.id, SlotsTable.trainerLogin, SlotsTable.description,
                                SlotsTable.slotDate, SlotsTable.startTime, SlotsTable.endTime,
                                SlotsTable.quantity, UsersTable.name
                            )
                            .select { SlotsTable.id inList bookedSlotIds }
                            .orderBy(SlotsTable.slotDate to SortOrder.ASC, SlotsTable.startTime to SortOrder.ASC)
                            .map {
                                SlotApiResponse(
                                    id = it[SlotsTable.id].value,
                                    trainerLogin = it[SlotsTable.trainerLogin],
                                    description = it[SlotsTable.description],
                                    slotDate = it[SlotsTable.slotDate].format(dateFormatter),
                                    startTime = it[SlotsTable.startTime].format(dbTimeFormatter),
                                    endTime = it[SlotsTable.endTime].format(dbTimeFormatter),
                                    quantity = it[SlotsTable.quantity],
                                    trainerName = it[UsersTable.name]
                                )
                            }
                    }
                    call.respond(bookedSlotsDetails)
                } else {
                    call.respond(emptyList<SlotApiResponse>())
                }

            } catch (e: Exception) {
                application.log.error("Failed to get client bookings for $clientLoginParam", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error fetching client bookings: ${e.localizedMessage}"))
            }
        }

        get("/admin/slots-clients") {
            try {
                val allBookings = DatabaseFactory.dbQuery { // ИСПРАВЛЕНО: тип выведен автоматически или можно указать List<SlotClientEntry>
                    SlotsClientsTable.selectAll()
                        .orderBy(SlotsClientsTable.slotId to SortOrder.ASC, SlotsClientsTable.clientLogin to SortOrder.ASC)
                        .map { SlotClientEntry(it[SlotsClientsTable.slotId], it[SlotsClientsTable.clientLogin]) } // Убедись, что SlotClientEntry определен и импортирован/доступен
                }
                call.respond(allBookings)
            } catch (e: Exception) {
                application.log.error("Failed to get all slot-client entries for admin", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Error fetching slot-client entries: ${e.localizedMessage}"))
            }
        }
    }
}