FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY backend/pom.xml .
COPY backend/.mvn .mvn
COPY backend/mvnw .

RUN chmod +x mvnw

RUN ./mvnw dependency:go-offline

COPY backend/src ./src

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
