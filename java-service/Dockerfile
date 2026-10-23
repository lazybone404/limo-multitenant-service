# limo-app backend (java) - uses locally built fat jar (fast; no maven in container)
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/limo-service-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
