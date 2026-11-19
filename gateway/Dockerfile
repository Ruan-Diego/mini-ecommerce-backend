# Imagem do gateway a partir do jar já gerado pelo Gradle (.\gradlew.bat :gateway:bootJar).
# Construir dentro do Docker baixaria o Gradle e todas as dependências a cada build: aqui o build
# continua sendo o do Gradle, e a imagem só empacota o resultado.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/gateway.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
