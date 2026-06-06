# SWIM DNOTAM Provider Validator, Raw YAML Deployment

## Prerequisites

- `oc` CLI authenticated to an OpenShift cluster
- Namespace `swim-demo` exists
- cert-manager installed with `swim-ca-issuer` ClusterIssuer
- RHBK (Keycloak) realm `swim` with client `swim-public-client`
- SWIM DNOTAM Provider deployed and accessible
- MariaDB instance available (or will be deployed separately)

## Deploy Order

Apply the manifests in this exact order. Each step depends on the previous one.

```bash
# 1. Password Secret (needed by the Certificate resource for keystore generation)
oc apply -f secret-certs-password.yaml -n swim-demo

# 2. Client Certificate (cert-manager generates PKCS12/JKS keystores, needs password secret)
oc apply -f certificate-client.yaml -n swim-demo

# 3. PersistentVolumeClaim (storage for validator data, must exist before Deployment)
oc apply -f pvc-data.yaml -n swim-demo

# 4. ConfigMap (application configuration)
oc apply -f configmap.yaml -n swim-demo

# 5. Service (must exist before Route)
oc apply -f service.yaml -n swim-demo

# 6. Deployment (depends on ConfigMap, password secret, client certs, and PVC)
oc apply -f deployment.yaml -n swim-demo

# 7. HorizontalPodAutoscaler (references the Deployment, singleton, max 1 replica)
oc apply -f hpa.yaml -n swim-demo

# 8. Route (depends on the Service)
oc apply -f route.yaml -n swim-demo

# 9. ServiceMonitor (references the Service, requires Prometheus Operator)
oc apply -f servicemonitor.yaml -n swim-demo
```

## Teardown

Remove in reverse order:

```bash
oc delete -f servicemonitor.yaml -n swim-demo
oc delete -f route.yaml -n swim-demo
oc delete -f hpa.yaml -n swim-demo
oc delete -f deployment.yaml -n swim-demo
oc delete -f service.yaml -n swim-demo
oc delete -f configmap.yaml -n swim-demo
oc delete -f pvc-data.yaml -n swim-demo
oc delete -f certificate-client.yaml -n swim-demo
oc delete -f secret-certs-password.yaml -n swim-demo
```
