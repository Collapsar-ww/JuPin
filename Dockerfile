FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY jupin-common/pom.xml ./jupin-common/
COPY jupin-pojo/pom.xml ./jupin-pojo/
COPY jupin-server/pom.xml ./jupin-server/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn package -DskipTests -B

FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/jupin-server/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
