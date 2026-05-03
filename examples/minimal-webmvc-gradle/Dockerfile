# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew examples/minimal-webmvc-gradle/gradlew \
    && ./gradlew publishToMavenLocal --no-daemon \
    && cd examples/minimal-webmvc-gradle \
    && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy
LABEL org.opencontainers.image.title="Swagger MCP Bridge Example Server" \
      org.opencontainers.image.description="Runnable Spring Boot WebMVC example exposing SpringDoc OpenAPI operations as MCP tools." \
      org.opencontainers.image.source="https://github.com/Neo1228/spring-boot-starter-swagger-mcp" \
      org.opencontainers.image.licenses="Apache-2.0" \
      io.modelcontextprotocol.server.name="io.github.Neo1228/swagger-mcp-bridge"

WORKDIR /app
COPY --from=builder /workspace/examples/minimal-webmvc-gradle/build/libs/*.jar /app/swagger-mcp-bridge-example.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/swagger-mcp-bridge-example.jar"]
