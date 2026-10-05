# Build from the repository root so the parent pom and shared module resolve:
#   docker build -f knowledge-service/Dockerfile -t vayvora/knowledge-service .
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn -B -pl knowledge-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /build/knowledge-service/target/knowledge-service-*.jar app.jar
USER app
EXPOSE 8007
ENTRYPOINT ["java", "-jar", "app.jar"]
