FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY backend/pom.xml /workspace/backend/pom.xml
COPY backend/src /workspace/backend/src
RUN mvn -q -pl backend -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/backend-1.0.0-SNAPSHOT.jar /app/backend.jar
EXPOSE 8010
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
