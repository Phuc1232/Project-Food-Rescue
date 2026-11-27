// AC-01..08 (T8.2): NotificationOutboxService responsibility.
package com.foodrescue.backend.notification.service
import com.foodrescue.backend.notification.*
import com.foodrescue.backend.notification.entity.Notification
import com.foodrescue.backend.notification.repository.NotificationRepository
import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.order.entity.CustomerOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.sql.Timestamp
import java.util.UUID
@Service
@Transactional(propagation=Propagation.MANDATORY)
class NotificationOutboxService(private val notifications: NotificationRepository,private val mapper: ObjectMapper,
    private val jdbc: NamedParameterJdbcTemplate,private val clock: Clock) {
    fun enqueue(recipient: Account,type: NotificationType,eventCode: String,title: String,payload: Map<String,Any?>,
        order: CustomerOrder?=null,dedupeKey: String?=null): Notification {
        require(eventCode.length in 1..64 && title.length in 1..255 && (dedupeKey==null || dedupeKey.length in 1..255))
        val json=mapper.writeValueAsString(payload);val now=clock.instant()
        if(dedupeKey==null) return notifications.saveAndFlush(Notification(recipient,order,type,json,NotificationStatus.PENDING,
            eventCode=eventCode,title=title,nextAttemptAt=now))
        // Atomic upsert preserves the business transaction on concurrent dedupe races.
        notifications.flush()
        jdbc.update("""insert into notification(id,created_at,updated_at,version,recipient_id,order_id,type,payload,status,event_code,title,dedupe_key,attempt_count,next_attempt_at)
            values (:id,:now,:now,0,:recipient,:order,:type,:payload,'PENDING',:event,:title,:key,0,:now)
            on conflict (dedupe_key) where dedupe_key is not null do nothing""",mapOf("id" to UUID.randomUUID(),"now" to Timestamp.from(now),
                "recipient" to recipient.id,"order" to order?.id,"type" to type.name,"payload" to json,"event" to eventCode,"title" to title,"key" to dedupeKey))
        return notifications.findByDedupeKey(dedupeKey)!!
    }
    fun enqueueSettlement(recipient: Account,eventCode: String,title: String,payload: Map<String,Any?>,order: CustomerOrder?=null,dedupeKey: String): Notification =
        enqueue(recipient,NotificationType.SETTLEMENT,eventCode,title,payload,order,dedupeKey)
    fun orderEvent(order: CustomerOrder,type: NotificationType,code: String,key: String="$code:${order.id}",
        customer: Boolean=true,store: Boolean=false,payload: Map<String,Any?> = emptyMap()) {
        val data=mapOf("orderId" to order.id,"orderCode" to order.orderCode,"status" to order.status.name)+payload
        val title=code.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }
        if(customer) enqueue(order.customer,type,code,title,data,order,"$key:CUSTOMER")
        if(store) enqueue(order.store.owner,type,code,title,data,order,"$key:STORE")
    }
}
