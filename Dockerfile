FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ENV MAVEN_OPTS="-Xmx512m"
RUN mvn -B -DskipTests package
ARG SERVICE
RUN cp ${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 app && useradd -u 10001 -g app app
WORKDIR /app
COPY --from=build /app.jar app.jar
RUN mkdir data && chown app:app data
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
