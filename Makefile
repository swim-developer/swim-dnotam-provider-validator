REGISTRY   ?= quay.io/masales
TAG        ?= latest
PLATFORMS   := linux/amd64,linux/arm64
MVN_NATIVE := -Dnative -DskipTests \
              -Dquarkus.native.container-build=true \
              -Dquarkus.native.container-runtime=podman
IMAGE_NAME := swim-dnotam-provider-validator

PARENT_DIR  := $(abspath $(dir $(abspath $(lastword $(MAKEFILE_LIST))))/..)
GITHUB_SSH  := git@github.com:swim-developer

SYNC_DEPS := swim-developer-root swim-developer-framework swim-developer-validators

.PHONY: help sync pull pull-deps install-deps jvm native-amd64 native-arm64 manifest push native

help:
	@echo ""
	@echo "  swim-dnotam-provider-validator — available targets"
	@echo "  ─────────────────────────────────────────────────────────"
	@echo ""
	@echo "    jvm             JVM multi-arch image — build + push"
	@echo "    native-amd64    Native amd64 image — build + push  (run on amd64)"
	@echo "    native-arm64    Native arm64 image — build + push  (run on arm64)"
	@echo "    manifest        Create multi-arch manifest from registry images"
	@echo "    push            Push manifest to registry"
	@echo "    native          Full native sequence (amd64 + arm64 + manifest + push)"
	@echo ""
	@echo "  Local dev:"
	@echo "    sync               Full setup: pull + pull-deps + install-deps"
	@echo "    pull               Pull this project from remote"
	@echo "    pull-deps          Clone missing deps + pull existing ones in $(PARENT_DIR)"
	@echo "    install-deps       Install all deps into local Maven repository"
	@echo ""
	@echo "  Variables: REGISTRY=$(REGISTRY)  TAG=$(TAG)"

sync: pull pull-deps install-deps

pull:
	@echo ""
	@echo "  ── Pull this project ────────────────────────────────────────"
	@git pull --ff-only
	@echo ""

pull-deps:
	@echo ""
	@echo "  ── Ensure sibling dependencies in $(PARENT_DIR) ─────────────"
	@for repo in $(SYNC_DEPS); do \
	  dir="$(PARENT_DIR)/$$repo"; \
	  if [ ! -d "$$dir" ]; then \
	    echo "  CLONE   $$repo"; \
	    git clone "$(GITHUB_SSH)/$$repo.git" "$$dir" --quiet; \
	  else \
	    printf "  PULL    $$repo ... "; \
	    git -C "$$dir" pull --ff-only --quiet 2>&1 && echo "ok" || echo "skipped (local changes or detached HEAD)"; \
	  fi; \
	done
	@echo ""

install-deps:
	@echo ""
	@echo "  ── Install dependencies into local Maven repository ─────────"
	@for repo in $(SYNC_DEPS); do \
	  dir="$(PARENT_DIR)/$$repo"; \
	  if [ ! -d "$$dir" ]; then \
	    echo "  SKIP    $$repo (not found — run: make pull-deps)"; \
	    continue; \
	  fi; \
	  mvn_cmd="mvn"; \
	  [ -f "$$dir/mvnw" ] && mvn_cmd="$$dir/mvnw"; \
	  if [ "$$repo" = "swim-developer-root" ]; then \
	    args="install -N -DskipTests -q"; \
	  else \
	    args="clean install -DskipTests -q"; \
	  fi; \
	  printf "  INSTALL $$repo ... "; \
	  "$$mvn_cmd" -f "$$dir/pom.xml" $$args && echo "ok" || { echo "FAIL"; exit 1; }; \
	done
	@echo ""
	@echo "  Done. Run: make jvm"
	@echo ""

jvm:
	-podman rmi  $(REGISTRY)/$(IMAGE_NAME):$(TAG) 2>/dev/null
	-podman manifest rm $(REGISTRY)/$(IMAGE_NAME):$(TAG) 2>/dev/null
	podman manifest create $(REGISTRY)/$(IMAGE_NAME):$(TAG)
	./mvnw clean package -DskipTests
	podman build --no-cache --platform $(PLATFORMS) \
		-f src/main/docker/Containerfile.jvm \
		--manifest $(REGISTRY)/$(IMAGE_NAME):$(TAG) .
	podman manifest push --all $(REGISTRY)/$(IMAGE_NAME):$(TAG) \
		docker://$(REGISTRY)/$(IMAGE_NAME):$(TAG)
	@echo "Pushed: $(REGISTRY)/$(IMAGE_NAME):$(TAG)  (JVM multi-arch)"

native-amd64:
	./mvnw clean package $(MVN_NATIVE) \
		-Dquarkus.native.container-runtime-options=--platform,linux/amd64
	podman build --no-cache --platform linux/amd64 \
		-f src/main/docker/Containerfile.native-micro \
		-t $(REGISTRY)/$(IMAGE_NAME):$(TAG)-amd64 .
	podman push $(REGISTRY)/$(IMAGE_NAME):$(TAG)-amd64
	@echo "Pushed: $(REGISTRY)/$(IMAGE_NAME):$(TAG)-amd64"

native-arm64:
	./mvnw clean package $(MVN_NATIVE) \
		-Dquarkus.native.container-runtime-options=--platform,linux/arm64
	podman build --no-cache --platform linux/arm64 \
		-f src/main/docker/Containerfile.native-micro \
		-t $(REGISTRY)/$(IMAGE_NAME):$(TAG)-arm64 .
	podman push $(REGISTRY)/$(IMAGE_NAME):$(TAG)-arm64
	@echo "Pushed: $(REGISTRY)/$(IMAGE_NAME):$(TAG)-arm64"

manifest:
	-podman rmi  $(REGISTRY)/$(IMAGE_NAME):$(TAG) 2>/dev/null
	-podman manifest rm $(REGISTRY)/$(IMAGE_NAME):$(TAG) 2>/dev/null
	podman manifest create $(REGISTRY)/$(IMAGE_NAME):$(TAG) \
		docker://$(REGISTRY)/$(IMAGE_NAME):$(TAG)-amd64 \
		docker://$(REGISTRY)/$(IMAGE_NAME):$(TAG)-arm64
	@echo "Manifest ready: $(REGISTRY)/$(IMAGE_NAME):$(TAG)"

push:
	podman manifest push --all $(REGISTRY)/$(IMAGE_NAME):$(TAG) \
		docker://$(REGISTRY)/$(IMAGE_NAME):$(TAG)
	@echo "Pushed: $(REGISTRY)/$(IMAGE_NAME):$(TAG)  (multi-arch)"

native: native-amd64 native-arm64 manifest push
