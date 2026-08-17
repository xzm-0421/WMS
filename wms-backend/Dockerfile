FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/wms-backend-1.0.0-SNAPSHOT.jar app.jarEXPOSE 9980/
ENTRYPOINT ["java", "-jar", "app.jar"]
