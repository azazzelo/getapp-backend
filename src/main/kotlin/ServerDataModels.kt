package ru.getapp

import kotlinx.serialization.Serializable

// --- Модели для аутентификации и базового пользователя ---
@Serializable data class LoginRequest(val login: String, val password: String)
@Serializable data class UserApiResponse(val login: String, val name: String, val role: String, val specialties: String? = null, val bio: String? = null)
// --- Модели для Слотов ---
@Serializable data class SlotApiResponse(val id: Long, val trainerLogin: String, val description: String, val slotDate: String, val startTime: String, val endTime: String, val quantity: Int, val trainerName: String? = null)
@Serializable data class CreateSlotRequest(val trainerLogin: String, val description: String, val slotDate: String, val startTime: String, val endTime: String, val quantity: Int)
@Serializable data class UpdateSlotRequest(val description: String?, val slotDate: String?, val startTime: String?, val endTime: String?, val quantity: Int?)
// --- Модели для Записей на слоты ---
@Serializable data class SlotBookingClientRequest(val clientLogin: String)
@Serializable data class BookingConfirmationResponse(val message: String, val slotId: Long, val clientLogin: String)
@Serializable data class SlotClientEntry(val slotId: Long, val clientLogin: String)
@Serializable data class AdminSlotClientRequest(val slotId: Long, val clientLogin: String)
// --- Модели для Профиля Пользователя ---
@Serializable data class UserProfileUpdateRequest(val name: String, val bio: String?, val specialties: String?)
// --- Модели для Администрирования Пользователей ---
@Serializable data class AdminCreateUserRequest(val login: String, val password: String, val name: String, val role: String, val specialties: String? = null, val bio: String? = null)
@Serializable data class AdminUpdateUserRequest(val name: String?, val role: String?, val specialties: String?, val bio: String?, val password: String? = null)
// --- Общие модели для ответов ---
@Serializable data class SimpleMessageResponse(val message: String)
@Serializable data class CreateResponse(val message: String, val id: Long)
@Serializable data class ErrorResponse(val error: String)

// НОВАЯ МОДЕЛЬ ДЛЯ УВЕДОМЛЕНИЙ

@Serializable
data class ApiUserNotification( // Используется и клиентом/тренером, и админом
    val id: Long,
    val userLogin: String? = null, // Добавим для админа, кому адресовано
    val message: String,
    val isRead: Boolean? = null,   // Добавим для админа
    val createdAt: String,
    val relatedSlotId: Long? = null // Добавим для админа
)