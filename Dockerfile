FROM gradle:8.7-jdk17 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . /home/gradle/project
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/trade-stocks-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 ${JAVA_OPTS:-} -Dserver.port=${PORT:-8080} -jar app.jar"]
