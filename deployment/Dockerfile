FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew \
    && ./gradlew :app-service:bootJar --no-daemon --no-configuration-cache -x test

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S franchise \
    && adduser -S -G franchise franchise

WORKDIR /app

COPY --from=builder --chown=franchise:franchise \
    /workspace/applications/app-service/build/libs/ApiFranchise.jar \
    /app/ApiFranchise.jar

USER franchise

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/ApiFranchise.jar"]
