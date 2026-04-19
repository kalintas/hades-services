# Multi-stage Docker build for Spring Boot application

#############################################################
#                                                           #
#     Stage 1: Build the application JAR                    #
#                                                           #
#############################################################

FROM maven:3.9.11-amazoncorretto-21 AS builder

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom files
COPY pom.xml pom.xml

# Download dependencies only for the main module and its dependencies
# Use parallel downloads and skip unnecessary plugins for faster resolution
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src


# Build the application
RUN mvn clean package -DskipTests -B

#############################################################
#                                                           #
#           Stage 2: Create the production image            #
#                                                           #
#############################################################

FROM amazoncorretto:21-alpine

# Set up Java environment
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Create application directory
RUN mkdir -p /opt/app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/services-*.jar /opt/app/app.jar

# Create non-root user for security
RUN addgroup -S spring && adduser -S -D spring -G spring
    
USER spring

# Expose port
EXPOSE 8080

# Health check
# HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
#    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]