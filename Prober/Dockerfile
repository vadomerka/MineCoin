# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY Prober/pom.xml Prober/pom.xml
COPY Prober/src Prober/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -f Prober/pom.xml package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/Prober/target/prober.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", \
    "-Djava.util.logging.SimpleFormatter.format=%1$tFT%1$tT.%1$tLZ %4$s %3$s - %5$s%6$s%n", "-jar", "app.jar"]
