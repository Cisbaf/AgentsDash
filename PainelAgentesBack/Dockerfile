# ===============================
# BUILD STAGE
# ===============================
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests


# ===============================
# RUNTIME STAGE
# ===============================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 9003

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java","-Xms128m","-Xmx484m","-XX:+UseG1GC","-XX:+UseContainerSupport","-jar","app.jar"]