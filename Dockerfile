FROM openjdk:8-alpine

COPY target/uberjar/firedraft.jar /firedraft/app.jar

# RUN apk add --no-cache curl

EXPOSE 3000

CMD ["java", "-jar", "/firedraft/app.jar"]

# "-Xmx228m"
