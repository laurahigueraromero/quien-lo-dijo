# Build: compila el jar con el propio Gradle Wrapper del proyecto (misma versión
# que se usa en desarrollo local, sin depender de un Gradle instalado en la imagen).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon

# Runtime: solo el JRE y el jar ya construido, imagen final mucho más ligera.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
