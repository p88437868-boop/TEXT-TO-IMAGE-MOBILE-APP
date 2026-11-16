package com.textpic.studio.data.repo

import com.textpic.studio.data.db.TemplateDao
import com.textpic.studio.data.db.TemplateEntity
import kotlinx.coroutines.flow.Flow

class TemplateRepository(private val dao: TemplateDao) {
    val templates: Flow<List<TemplateEntity>> = dao.observeAll()

    suspend fun ensureSeeded() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        val seeds = listOf(
            "Greeting" to "Hi {name}! Thanks for stopping by. How can I help you today?",
            "Price quote" to "Hi {name}, the {product} is {price}. Would you like me to reserve one for you?",
            "Still interested?" to "Hi {name}, just checking in — are you still interested in the {product}?",
            "Delivery info" to "Your {product} is on the way. Delivery is expected soon. Thank you for your patience!",
            "Thank you" to "Thank you for your order, {name}! We really appreciate your business.",
            "Follow-up" to "Hi {name}, did you have a chance to look at the {product}? I'm here if you have questions.",
        )
        seeds.forEachIndexed { index, (title, body) ->
            dao.insert(
                TemplateEntity(
                    title = title,
                    body = body,
                    createdAt = now - index,
                ),
            )
        }
    }

    suspend fun add(title: String, body: String): Long =
        dao.insert(TemplateEntity(title = title, body = body, createdAt = System.currentTimeMillis()))

    suspend fun update(template: TemplateEntity) = dao.update(template)

    suspend fun delete(template: TemplateEntity) = dao.delete(template)

    suspend fun all(): List<TemplateEntity> = dao.getAll()

    suspend fun deleteAll() = dao.deleteAll()
}
