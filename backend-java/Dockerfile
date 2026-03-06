FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml /workspace/pom.xml
COPY backend-java/pom.xml /workspace/backend-java/pom.xml
COPY backend-java/src /workspace/backend-java/src
RUN mvn -q -pl backend-java -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend-java/target/backend-java-1.0.0-SNAPSHOT.jar /app/backend-java.jar
EXPOSE 8010
ENTRYPOINT ["java", "-jar", "/app/backend-java.jar"]
