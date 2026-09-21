FROM eclipse-temurin:21-jdk-jammy@sha256:4e61d0b3471137e239397789661f6adeaf8db9adcfae45916b22f628c8a32dc0 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY ledger-core/pom.xml ledger-core/pom.xml
COPY loan-service/pom.xml loan-service/pom.xml
COPY loan-bff/pom.xml loan-bff/pom.xml
RUN chmod +x mvnw && ./mvnw -B -ntp -pl loan-bff -am dependency:go-offline
COPY loan-bff/src/ loan-bff/src/
RUN ./mvnw -B -ntp -pl loan-bff -am -DskipTests package
FROM eclipse-temurin:21-jre-jammy@sha256:9603446cf51296842eab342514857cf7ab55a8891cd564e6c68ec237fae65a16
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10003 bff && useradd --system --uid 10003 --gid bff bff
WORKDIR /app
COPY --from=build --chown=bff:bff /workspace/loan-bff/target/loan-bff-0.1.0-SNAPSHOT.jar /app/bff.jar
USER bff
EXPOSE 8082
HEALTHCHECK --interval=5s --timeout=3s --start-period=40s --retries=15 CMD curl --fail --silent http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java","-jar","/app/bff.jar"]
