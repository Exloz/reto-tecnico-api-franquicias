FROM alpine:3.22.1@sha256:4bcff63911fcb4448bd4fdacec207030997caf25e9bea4045fa6c8c44de311d1 AS certificates

ARG RDS_GLOBAL_BUNDLE_SHA256=e5bb2084ccf45087bda1c9bffdea0eb15ee67f0b91646106e466714f9de3c7e3

RUN apk add --no-cache curl \
    && curl --fail --silent --show-error --location \
        https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
        --output /aws-rds-global-bundle.pem \
    && printf '%s  %s\n' "$RDS_GLOBAL_BUNDLE_SHA256" /aws-rds-global-bundle.pem | sha256sum -c -

FROM flyway/flyway:12.4.0@sha256:5be18367a9b3979a9f37234c371b80da457a448fb9577bd94d4bf24d923fafac

RUN useradd --uid 10001 --home-dir /flyway --no-create-home --shell /usr/sbin/nologin flyway_runner \
    && mkdir -p /flyway/conf /flyway/.postgresql

COPY --from=certificates /aws-rds-global-bundle.pem /flyway/conf/aws-rds-global-bundle.pem

RUN chmod 0444 /flyway/conf/aws-rds-global-bundle.pem \
    && ln -s /flyway/conf/aws-rds-global-bundle.pem /flyway/.postgresql/root.crt

COPY --chown=flyway_runner flyway.toml /flyway/conf/flyway.toml
COPY --chown=flyway_runner migrations /flyway/sql

USER flyway_runner

CMD ["migrate", "validate"]
