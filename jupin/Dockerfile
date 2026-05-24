FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . .
RUN mvn -pl jupin-server -am package -Dmaven.test.skip=true -B

FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app
COPY --from=builder /build/jupin-server/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
