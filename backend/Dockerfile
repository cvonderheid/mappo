FROM eclipse-temurin:21-jre

WORKDIR /app

# The Maven reactor packages the backend jar before the Docker deploy phase.
COPY target/backend-*.jar /app/backend.jar

EXPOSE 8010
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
