# Stage 1: Build — Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml first so dependency layer is cached separately from source code.
# This means 'mvn dependency:go-offline' is only re-run when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the JAR (skip tests — tests run in CI)
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime — minimal JRE image
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
