FROM alpine:3.22.1@sha256:4bcff63911fcb4448bd4fdacec207030997caf25e9bea4045fa6c8c44de311d1

ARG RDS_GLOBAL_BUNDLE_SHA256=e5bb2084ccf45087bda1c9bffdea0eb15ee67f0b91646106e466714f9de3c7e3

RUN apk add --no-cache aws-cli ca-certificates jq openssl postgresql17-client \
    && apk add --no-cache --virtual .download-deps curl \
    && curl --fail --silent --show-error --location \
        https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
        --output /etc/ssl/certs/aws-rds-global-bundle.pem \
    && printf '%s  %s\n' "$RDS_GLOBAL_BUNDLE_SHA256" /etc/ssl/certs/aws-rds-global-bundle.pem | sha256sum -c - \
    && apk del .download-deps \
    && addgroup --system --gid 10001 bootstrap \
    && adduser --system --disabled-password --no-create-home --uid 10001 --ingroup bootstrap bootstrap

COPY entrypoint.sh /usr/local/bin/database-bootstrap

RUN chmod 0555 /usr/local/bin/database-bootstrap

USER 10001:10001

ENTRYPOINT ["/usr/local/bin/database-bootstrap"]
