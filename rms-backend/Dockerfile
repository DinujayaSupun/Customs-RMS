# =====================================================================
# Stage 1 — BUILD: compile the Spring Boot app into a runnable .jar
# =====================================================================
# We use the official Maven image (which already contains Java 17 + Maven),
# so we don't rely on the ./mvnw wrapper. This avoids Windows line-ending
# problems that can break ./mvnw inside a Linux container.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy ONLY pom.xml first and download all dependencies.
# Docker caches this layer, so dependencies are re-downloaded only when
# pom.xml changes — not every time you tweak your source code.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source and build the jar. Tests are skipped here because
# they need a database; we run tests separately with run-all-tests.ps1.
COPY src src
RUN mvn clean package -DskipTests -B

# =====================================================================
# Stage 2 — RUN: a small image that only has the Java runtime + our jar
# =====================================================================
# The build image (with Maven, source, .m2 cache) is large and stays
# behind. Only this final, slim image is shipped.
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the jar produced in Stage 1 into this image and name it app.jar
COPY --from=build /app/target/rms-backend-0.0.1-SNAPSHOT.jar app.jar

# Document that the app listens on 8080 (matches server.port)
EXPOSE 8080

# The command that runs when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]
