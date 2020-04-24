FROM openjdk:8-alpine

COPY target/uberjar/firedraft.jar /firedraft/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/firedraft/app.jar"]
