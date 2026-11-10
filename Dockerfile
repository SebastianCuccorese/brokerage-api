# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/brokerage-api-*.jar extract --layers --launcher --destination /workspace/layers

FROM eclipse-temurin:21.0.13_9-jre-noble

RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app
USER 10001:10001
WORKDIR /app

# Ordered from least to most frequently changing so a code change only rebuilds the last layer.
COPY --from=build --chown=10001:10001 /workspace/layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/layers/application/ ./

# Sizes the heap from the container memory limit instead of the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# Exec form: the JVM is PID 1 and receives SIGTERM, so Spring shuts down gracefully.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
