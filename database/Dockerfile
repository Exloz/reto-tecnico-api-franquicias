FROM flyway/flyway:12.4.0

RUN useradd --uid 10001 --home-dir /flyway --no-create-home --shell /usr/sbin/nologin flyway_runner

COPY --chown=flyway_runner flyway.toml /flyway/conf/flyway.toml
COPY --chown=flyway_runner migrations /flyway/sql

USER flyway_runner

CMD ["migrate", "validate"]
