FROM eclipse-temurin:17-jdk

COPY build/libs/*.jar .
CMD java -jar *.jar
