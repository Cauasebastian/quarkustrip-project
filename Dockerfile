FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
COPY . .
RUN mvn -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /deployments
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/${MODULE}/target/quarkus-app/*.jar ./
COPY --from=build /workspace/${MODULE}/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/${MODULE}/target/quarkus-app/quarkus/ ./quarkus/
EXPOSE 8080
ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
