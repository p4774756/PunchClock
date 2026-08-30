# Build from monorepo root (clickClick/). Render: Dockerfile Path = server/Dockerfile
FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app

COPY pom.xml .
COPY shared/pom.xml shared/pom.xml
COPY shared/src shared/src
COPY client/pom.xml client/pom.xml
COPY server/pom.xml server/pom.xml
COPY server/src server/src

RUN mvn -pl server -am package -DskipTests

FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

COPY --from=build /app/server/target/clickClick-server.jar app.jar

ENV PORT=3000
EXPOSE 3000

CMD ["java", "-jar", "app.jar"]
