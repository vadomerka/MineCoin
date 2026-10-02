# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY FinanceService/pom.xml FinanceService/pom.xml
COPY FinanceService/src FinanceService/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f FinanceService/pom.xml package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/FinanceService/target/finance-service.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
