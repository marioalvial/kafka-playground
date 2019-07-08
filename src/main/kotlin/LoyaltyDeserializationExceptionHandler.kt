import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.errors.DeserializationExceptionHandler
import org.apache.kafka.streams.processor.ProcessorContext
import java.util.*

abstract class LoyaltyDeserializationExceptionHandler(
    private val bootstrapServers: String,
    private val deadLetterTopic: String,
    private val applicationName: String,
    private val mapper: ObjectMapper
) : DeserializationExceptionHandler {

    private lateinit var producer: KafkaProducer<String, String>

    override fun handle(
        context: ProcessorContext,
        record: ConsumerRecord<ByteArray, ByteArray>,
        exception: Exception
    ): DeserializationExceptionHandler.DeserializationHandlerResponse {
        val key = record.key().toString()
        val data = mapper.writeValueAsString(record.value())
        val deadLetterMessage = createDeadLetterMessage(key, data, record, context, exception)

        producer.send(ProducerRecord(deadLetterTopic, applicationName, mapper.writeValueAsString(deadLetterMessage)))

        return DeserializationExceptionHandler.DeserializationHandlerResponse.CONTINUE
    }

    private fun createDeadLetterMessage(
        key: String,
        data: String,
        record: ConsumerRecord<ByteArray, ByteArray>,
        context: ProcessorContext,
        exception: Exception
    ) = DeadLetterMessage(
        key = key,
        data = data,
        fromTopic =
        record.topic(),
        streamId = context.applicationId(),
        throwable = exception,
        details = details()
    )

    open fun details() = mapOf<String, Any>()

    override fun configure(configs: MutableMap<String, *>?) {
        val producerConfig = Properties().also {
            it[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
            it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
            it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
            it[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
            it[ProducerConfig.ACKS_CONFIG] = "all"
            it[ProducerConfig.RETRIES_CONFIG] = Integer.MAX_VALUE.toString()
            it[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "5"
        }

        producer = KafkaProducer(producerConfig)
    }
}