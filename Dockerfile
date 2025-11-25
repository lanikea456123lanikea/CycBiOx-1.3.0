# Use OpenJDK 21 with JavaFX support
FROM openjdk:21-jdk-slim

# Install necessary packages
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    git \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libgl1-mesa-glx \
    libgtk-3-0 \
    && rm -rf /var/lib/apt/lists/*

# Set up working directory
WORKDIR /app

# Copy project files
COPY . .

# Make gradlew executable
RUN chmod +x gradlew

# Set JAVA_HOME
ENV JAVA_HOME=/usr/local/openjdk-21

# Expose any ports if needed (for potential web interfaces)
EXPOSE 8080

# Default command
CMD ["./gradlew", "build"]