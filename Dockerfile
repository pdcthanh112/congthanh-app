

# Fetching latest version of Java
FROM openjdk:18


# Setting up work directory
WORKDIR /backend/app

# Copy the jar file into our app
COPY ./target/spring-0.0.1-SNAPSHOT.jar /app

# Exposing port 
EXPOSE 5000

# Starting the application
CMD ["java", "-jar", "spring-0.0.1-SNAPSHOT.jar"]





FROM node:latest

# Create app directory
WORKDIR /backend/auth

COPY package*.json ./

RUN npm install

COPY . .

EXPOSE 8000
CMD [ "node", "app.ts" ]








RUN rm -rf /var/lib/apt/lists/*
