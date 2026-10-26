# Build stage: compile and package the Spring Boot jar with the Maven wrapper (JDK 21).
FROM eclipse-temurin:21.0.12_8-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Run stage: JRE only, non-root user.
FROM eclipse-temurin:21.0.12_8-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
