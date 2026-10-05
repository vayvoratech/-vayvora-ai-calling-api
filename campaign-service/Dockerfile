# Build from the repository root so the parent pom and shared module resolve:
#   docker build -f campaign-service/Dockerfile -t vayvora/campaign-service .
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn -B -pl campaign-service -am package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /build/campaign-service/target/campaign-service-*.jar app.jar
USER app
EXPOSE 8005
ENTRYPOINT ["java", "-jar", "app.jar"]
