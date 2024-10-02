#!/bin/bash

kubectl apply -f deployment.yaml
kubectl describe pods | egrep "(cpu|memory|ephemeral)" | tail -n 3
kubectl delete -f deployment.yaml
