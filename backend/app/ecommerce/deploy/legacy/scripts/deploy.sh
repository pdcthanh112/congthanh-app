#!/bin/bash

set -e

# Đặt context cho kubectl (nếu bạn có nhiều cluster)
kubectl config use-context dev-cluster

echo "Deploying to development environment..."

echo "Deploying logging services..."
kubectl apply -f k8s/logging/ -n dev

echo "Deploying monitoring services..."
kubectl apply -f k8s/monitoring/ -n dev

echo "Deploying microservices..."
kubectl apply -f k8s/services/ -n dev

echo "Development deployment completed successfully!"