# STAGE 1: BUILD
FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:resolve -B
COPY src ./src
RUN mvn clean package -DskipTests -B && mv target/*.jar target/application.jar
RUN java -Djarmode=tools -jar target/application.jar extract --layers --destination extracted

# STAGE 2: RUNTIME
FROM docker.io/library/eclipse-temurin:25-jre AS runtime
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup appuser
WORKDIR /app
COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
