#!/bin/bash

# Build and run QuPath Cell Phenotype Manager Plugin in Docker

echo "Building QuPath Cell Phenotype Manager Plugin..."

# Build the Docker image
docker-compose build

# Start the development environment
echo "Starting development environment..."
docker-compose up -d qupath-plugin-dev

# Build the plugin
echo "Building the plugin..."
docker-compose exec qupath-plugin-dev ./gradlew clean build

# Show build results
echo "Build completed! Check the build/libs directory for the plugin JAR file."
docker-compose exec qupath-plugin-dev ls -la build/libs/

# Keep the container running for development
echo "Development environment is ready. Use 'docker-compose exec qupath-plugin-dev bash' to enter the container."
echo "To stop the environment, run 'docker-compose down'"