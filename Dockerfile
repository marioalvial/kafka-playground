FROM openjdk:8-jdk-alpine

ENV JAR_NAME kafka-playground-1.0-SNAPSHOT

COPY build/libs/$JAR_NAME.jar $JAR_NAME.jar

ADD wait-for-it.sh wait-for-it.sh

RUN chmod +x /wait-for-it.sh

ENTRYPOINT [ "/wait-for-it.sh", "kafka:9092", "--" ]

CMD java -jar $JAR_NAME.jar
