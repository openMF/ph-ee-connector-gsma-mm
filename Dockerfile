FROM openjdk:17

COPY build/libs/*.jar .
CMD java -jar *.jar
