# Etapa de build
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew clean bootJar --no-daemon

# Etapa de producción
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Si tienes recursos estáticos JS (npm, node_modules, build), puedes copiarlos aquí si es necesario
# COPY --from=build /app/src/main/resources/static /app/static

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
