import java.time.Instant

class DeadLetterMessage(
    val key: String,
    val data: String,
    val fromTopic: String,
    val streamId: String,
    throwable: Throwable,
    val details: Map<String, Any> = mapOf()
) {
    val throwableMessage = throwable.message ?: ""
    val throwableLocalizedMessage = throwable.localizedMessage ?: ""
    val stackTrace = throwable.stackTrace.joinToString("\n")

    val timestamp = Instant.now()
}