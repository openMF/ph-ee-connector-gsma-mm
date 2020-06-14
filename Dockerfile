FROM openjdk:13

COPY target/*.jar .
CMD java -jar *.jar

