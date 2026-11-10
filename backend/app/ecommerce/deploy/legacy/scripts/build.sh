#!/bin/bash
# scripts/build-and-push.sh

# Định nghĩa các biến
PROJECT_ID="your-project-id"
REGISTRY="gcr.io/${PROJECT_ID}"
VERSION=$(git rev-parse --short HEAD)

# Build từng service
build_service() {
    local service_name=$1
    
    # Build image
    docker build \
        -t ${REGISTRY}/${service_name}:${VERSION} \
        -t ${REGISTRY}/${service_name}:latest \
        ./microservices/${service_name}
    
    # Push image
    docker push ${REGISTRY}/${service_name}:${VERSION}
    docker push ${REGISTRY}/${service_name}:latest
}

# Build và push từng service
build_service "service1"
build_service "service2"