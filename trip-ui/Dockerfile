FROM node:24.14.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8180
ARG VITE_KEYCLOAK_REALM=trip
ARG VITE_KEYCLOAK_CLIENT_ID=trip-gateway
ARG VITE_SHOW_DEV_CREDENTIALS=true
ARG VITE_JAEGER_BASE_URL=http://localhost:16686
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} \
    VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL} \
    VITE_KEYCLOAK_REALM=${VITE_KEYCLOAK_REALM} \
    VITE_KEYCLOAK_CLIENT_ID=${VITE_KEYCLOAK_CLIENT_ID} \
    VITE_SHOW_DEV_CREDENTIALS=${VITE_SHOW_DEV_CREDENTIALS} \
    VITE_JAEGER_BASE_URL=${VITE_JAEGER_BASE_URL}
RUN npm run build

FROM nginx:1.27.4-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 3000
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -qO- http://localhost:3000/healthz || exit 1
