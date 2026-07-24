FROM quay.io/keycloak/keycloak:26.1.4 AS builder

ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true

COPY --chown=keycloak:keycloak infra/keycloak/themes/trip /opt/keycloak/themes/trip

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.1.4

COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
