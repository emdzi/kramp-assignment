FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY . .

ARG MODULE
RUN chmod +x aggregator-service/mvnw \
    && ./aggregator-service/mvnw -B -f pom.xml -pl "${MODULE}" -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app
ARG MODULE
ARG APP_PORT=8080
COPY --from=build /workspace/${MODULE}/target/${MODULE}-1.0.0-SNAPSHOT.jar app.jar

EXPOSE ${APP_PORT}
USER 10001
ENTRYPOINT ["java", "--enable-preview", "-jar", "/app/app.jar"]
