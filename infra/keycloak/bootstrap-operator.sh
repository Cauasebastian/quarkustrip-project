#!/usr/bin/env bash
set -euo pipefail

export HOME=/tmp
KCADM=/opt/keycloak/bin/kcadm.sh

"${KCADM}" config credentials \
  --server http://keycloak:8080 \
  --realm master \
  --user "${KEYCLOAK_ADMIN:-admin}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD:-admin}"

if ! "${KCADM}" get roles/OPERATOR -r trip >/dev/null 2>&1; then
  "${KCADM}" create roles -r trip -s name=OPERATOR \
    -s 'description=Cria ofertas, pacotes e reservas assistidas'
fi

if ! "${KCADM}" get users -r trip -q exact=true -q username=operator |
  grep -Eq '"username"[[:space:]]*:[[:space:]]*"operator"'; then
  "${KCADM}" create users -r trip \
    -s username=operator \
    -s enabled=true \
    -s email=operator@trip.local \
    -s emailVerified=true \
    -s firstName=Trip \
    -s lastName=Operator
fi

"${KCADM}" set-password -r trip --username operator --new-password operator
"${KCADM}" add-roles -r trip --uusername operator --rolename USER --rolename OPERATOR
