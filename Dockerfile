FROM eclipse-temurin:21-jre

COPY build/libs/*.jar .
CMD java -jar *.jar
