import com.fasterxml.jackson.databind.ObjectMapper
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import java.time.Duration
import java.util.*

private const val STREAM_DURATION_SHUTDOWN: Long = 5

abstract class KafkaAbstractStream<I : SpecificRecord, O : SpecificRecord> {
    protected abstract val bootstrapServers: String
    protected abstract val applicationId: String
    protected abstract val inputTopic: String
    protected abstract val outputTopic: String
    protected abstract val dlqTopic: String
    protected abstract val schemaRegistryUrl: String
    protected abstract val objectMapper: ObjectMapper

    private val streamConfig by lazy {
        Properties().also {
            it[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServers
            it[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            it[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String()::class.java.name
            it[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = SpecificAvroSerde::class.java.name
            it[AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = schemaRegistryUrl
            it[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = StreamsConfig.EXACTLY_ONCE
            it[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] =
                ApplicationDeserializationExceptionHandler::class.java.name
        }
    }

    fun run() {
        val streamBuilder = createStreamBuilder()
        val stream = KafkaStreams(streamBuilder.build(), streamConfig)
        stream.start()
        Runtime.getRuntime().addShutdownHook(Thread { stream.close(Duration.ofSeconds(STREAM_DURATION_SHUTDOWN)) })
    }

    private fun createStreamBuilder(): StreamsBuilder {
        val serdesConfig = mapOf(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to schemaRegistryUrl)
        val inputAvroSerdes = SpecificAvroSerde<I>().apply { configure(serdesConfig, false) }
        val outputAvroSerdes = SpecificAvroSerde<O>().apply { configure(serdesConfig, false) }
        val consumed = Consumed.with<String, I>(Serdes.String(), inputAvroSerdes)
        val produced = Produced.with(Serdes.String(), outputAvroSerdes)

        return StreamsBuilder().apply {
            stream<String, I>(inputTopic, consumed)
                .let { processMessage(it) }
                ?.to(outputTopic, produced)
        }
    }
//        return StreamsBuilder().apply {
//            initialProcess(stream<String, I>(inputTopic, consumed))
//                .branch({ _, value -> value is DeadLetterMessage }, { _, value -> value is SpecificRecord })
//                .let {
//                    it[0].to(dlqTopic)
//                    it[1]
//                        .mapValues { value -> runCatching { value as O }.getOrNull() }
//                        .filter { _, value -> value != null }
//                        .to(outputTopic, produced)
//                }
//        }

    private fun processMessage(it: KStream<String, I>): KStream<String, O>? {
        return runCatching { process(it) }
            .onFailure { handleException(it) }
            .getOrNull()
    }

//    private fun initialProcess(stream: KStream<String, I>): KStream<String, out Any> = runCatching { process(stream) }
//        .onFailure { handleException(it) }
//        .getOrElse {
//            stream
//                .mapValues { _, value -> objectMapper.writeValueAsString(value) }
//                .mapValues { key, data -> DeadLetterMessage(key, data, inputTopic, applicationId, it) }
//        }

    abstract fun process(stream: KStream<String, I>): KStream<String, O>

    abstract fun handleException(throwable: Throwable)
}