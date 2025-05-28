package ru.getapp

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class UserApiResponse(
    val login: String,
    val name: String,
    val role: String
)

@Serializable
data class SlotApiResponse(
    val id: Long,
    val trainerLogin: String,
    val description: String,
    val slotDate: String,           // Формат "YYYY-MM-DD"
    val startTime: String,          // Формат "HH:MM:SS" (рекомендуемый для API)
    val endTime: String,            // Формат "HH:MM:SS" (рекомендуемый для API)
    val quantity: Int,
    val trainerName: String? = null // Имя тренера, если получено через JOIN
)

@Serializable
data class CreateSlotRequest(
    val trainerLogin: String,
    val description: String,
    val slotDate: String,           // Ожидаемый формат "YYYY-MM-DD"
    val startTime: String,          // Ожидаемый формат "HH:mm" или "HH:mm:ss"
    val endTime: String,            // Ожидаемый формат "HH:mm" или "HH:mm:ss"
    val quantity: Int
)

@Serializable
data class UpdateSlotRequest( // Для обновления слота, все поля опциональны
    val description: String? = null,
    val slotDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val quantity: Int? = null
)

@Serializable
data class SlotBookingRequest( // Для тела запроса POST /slots/{slotId}/book
    val clientLogin: String    // slotId будет параметром пути
)

@Serializable
data class BookingResponse( // Общий ответ для операций бронирования/отмены
    val message: String,
    val slotId: Long? = null,
    val clientLogin: String? = null
)

@Serializable
data class SlotClientEntry( // Для представления записи из таблицы slots_clients
    val slotId: Long,
    val clientLogin: String
)

// Если понадобятся модели для админской части (CRUD для users, slots, slots_clients)
// Можно будет добавить их сюда или создать отдельные.
// Например, для создания пользователя админом (включая пароль):
@Serializable
data class AdminCreateUserRequest(
    val login: String,
    val password: String,
    val name: String,
    val role: String,
    val specialties: String? = null,
    val bio: String? = null
)

// Для обновления пользователя админом:
@Serializable
data class AdminUpdateUserRequest(
    val name: String?,
    val role: String?, // Админ может менять роль (кроме своей)
    val specialties: String?,
    val bio: String?,
    val password: String? = null // Опционально для смены пароля
)