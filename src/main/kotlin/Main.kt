import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.Properties

fun main() {
    val consumerConfig = Properties().also {
        it[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "true"
        it[ConsumerConfig.GROUP_ID_CONFIG] = "kafka-playground-consumer-group"
        it[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = "1000"
    }
    val consumer = KafkaConsumer<String, String>(consumerConfig)
    Runtime.getRuntime().addShutdownHook(Thread{consumer.wakeup()})
    consumer.subscribe(listOf("twitter-connector-status"))
    while (true){
    val records = consumer.poll(Duration.ZERO)
        records.forEach { println("KEY ${it.key()} - VALUE ${it.value().substringAfter("\"payload\":")}")
        }
    }
}