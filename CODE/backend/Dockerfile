FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system foodrescue && useradd --system --gid foodrescue foodrescue
COPY --from=build /workspace/build/libs/foodrescue.jar app.jar
USER foodrescue
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
