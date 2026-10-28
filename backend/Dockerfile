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
# /data/attachments is the mount point of the attachments volume. A new named volume copies this
# folder's owner and mode, so the non-root user can write to it and other users can't read it.
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/attachments \
    && chown app:app /data/attachments \
    && chmod 0750 /data/attachments
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
