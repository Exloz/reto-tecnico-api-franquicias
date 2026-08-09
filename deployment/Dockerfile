FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew \
    && if [ -f ApiFranchise.jar ]; then \
        mkdir -p applications/app-service/build/libs \
        && cp ApiFranchise.jar applications/app-service/build/libs/ApiFranchise.jar; \
    else \
        ./gradlew :app-service:bootJar --no-daemon --no-configuration-cache -x test; \
    fi

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

ARG RDS_GLOBAL_BUNDLE_SHA256=e5bb2084ccf45087bda1c9bffdea0eb15ee67f0b91646106e466714f9de3c7e3

RUN apk add --no-cache --virtual .download-deps curl \
    && mkdir -p /app/certs \
    && curl --fail --silent --show-error --location \
        https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
        --output /app/certs/aws-rds-global-bundle.pem \
    && printf '%s  %s\n' "$RDS_GLOBAL_BUNDLE_SHA256" /app/certs/aws-rds-global-bundle.pem | sha256sum -c - \
    && chmod 0444 /app/certs/aws-rds-global-bundle.pem \
    && apk del .download-deps \
    && addgroup -S franchise \
    && adduser -S -G franchise franchise

WORKDIR /app

ENV DB_SSL_ROOT_CERT=/app/certs/aws-rds-global-bundle.pem

COPY --from=builder --chown=franchise:franchise \
    /workspace/applications/app-service/build/libs/ApiFranchise.jar \
    /app/ApiFranchise.jar

USER franchise

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/ApiFranchise.jar"]
