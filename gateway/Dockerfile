# Imagem do gateway a partir do jar já gerado pelo Gradle (.\gradlew.bat :gateway:assemble).
# Construir dentro do Docker baixaria o Gradle e todas as dependências a cada build: aqui o build
# continua sendo o do Gradle, e a imagem só empacota o resultado (o jar e o OpenTelemetry Java agent).
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/gateway.jar app.jar
COPY build/otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar

# Para onde o agent manda traces e logs (o collector do perfil `observabilidade` no Compose).
# Métricas saem pelo Actuator (/actuator/prometheus), que o Prometheus lê.
ENV OTEL_METRICS_EXPORTER=none

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/otel/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
