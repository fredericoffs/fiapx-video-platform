FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# video-worker invoca ffmpeg como subprocesso (ver ADR-007 no Obsidian) — precisa do binário no runtime
RUN apk add --no-cache ffmpeg
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
