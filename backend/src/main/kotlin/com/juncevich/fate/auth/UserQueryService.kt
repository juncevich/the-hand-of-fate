package com.juncevich.fate.auth

import com.juncevich.fate.auth.internal.persistence.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserQueryService(
    private val userRepository: UserRepository,
) {
    fun findById(id: UUID): User? = userRepository.findById(id).orElse(null)

    fun findByEmail(email: String): User? = userRepository.findByEmail(email)

    fun findAllByEmailIn(emails: Collection<String>): List<User> = userRepository.findAllByEmailIn(emails)

    fun findByTelegramId(telegramId: Long): User? = userRepository.findByTelegramId(telegramId)
}
