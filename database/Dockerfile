FROM flyway/flyway:12.4.0

COPY flyway.toml /flyway/conf/flyway.toml
COPY migrations /flyway/sql

CMD ["validate", "migrate"]
