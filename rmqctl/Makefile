# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

GO ?= go
VERSION ?= 1.0.0
GIT_COMMIT ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo unknown)
BUILD_DATE ?= $(shell date -u +%Y-%m-%dT%H:%M:%SZ)
BUILD_DIR ?= $(CURDIR)/bin
PACKAGE_DIR ?= $(BUILD_DIR)/release
LEGAL_DIR ?= $(BUILD_DIR)/legal
TARGETS := darwin_amd64 darwin_arm64 linux_amd64 linux_arm64 windows_amd64 windows_arm64
LDFLAGS = -s -w \
	-X 'github.com/apache/rocketmq-dashboard/rmqctl/cmd.CLIVersion=$(VERSION)' \
	-X 'github.com/apache/rocketmq-dashboard/rmqctl/cmd.GitCommit=$(GIT_COMMIT)' \
	-X 'github.com/apache/rocketmq-dashboard/rmqctl/cmd.BuildDate=$(BUILD_DATE)'

CATALOG_SOURCE := ../server/src/main/resources/tool-catalog/tools
CATALOG_GENERATED := internal/catalog/catalog_gen.go
CATALOG_GENERATOR := ./internal/catalog/generate
CATALOG_MARKDOWN := ../docs/generated/rmq-tools.md
CATALOG_SDK := ../docs/generated/rmq-tools.json

.PHONY: build build-all package checksum release clean catalog-generate catalog-verify test test-race vet lint fmt verify ci completion install

catalog-generate:
	$(GO) run $(CATALOG_GENERATOR) -input-dir $(CATALOG_SOURCE) -output $(CATALOG_GENERATED) \
		-markdown $(CATALOG_MARKDOWN) -sdk $(CATALOG_SDK)

catalog-verify:
	$(GO) run $(CATALOG_GENERATOR) -input-dir $(CATALOG_SOURCE) -output $(CATALOG_GENERATED) \
		-markdown $(CATALOG_MARKDOWN) -sdk $(CATALOG_SDK) -check

# Catalog sources live outside the Go module; do not reuse cached freshness results.
test:
	$(GO) test -count=1 ./...

test-race:
	$(GO) test -race -count=1 ./...

vet: catalog-verify
	$(GO) vet ./...

fmt:
	@out=`gofmt -l . 2>&1`; if [ -n "$$out" ]; then echo "$$out"; exit 1; fi

lint: vet
	@command -v golangci-lint >/dev/null 2>&1 && golangci-lint run ./... || echo "golangci-lint not installed; skipping"

verify: fmt test-race vet build

ci: fmt test-race vet build-all

build: catalog-verify
	@mkdir -p $(BUILD_DIR)
	CGO_ENABLED=0 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl main.go

build-all: catalog-verify
	@mkdir -p $(BUILD_DIR)
	CGO_ENABLED=0 GOOS=darwin  GOARCH=amd64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-darwin-amd64    main.go
	CGO_ENABLED=0 GOOS=darwin  GOARCH=arm64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-darwin-arm64    main.go
	CGO_ENABLED=0 GOOS=linux   GOARCH=amd64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-linux-amd64     main.go
	CGO_ENABLED=0 GOOS=linux   GOARCH=arm64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-linux-arm64     main.go
	CGO_ENABLED=0 GOOS=windows GOARCH=amd64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-windows-amd64.exe main.go
	CGO_ENABLED=0 GOOS=windows GOARCH=arm64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-windows-arm64.exe main.go

package: build-all
	@rm -rf $(PACKAGE_DIR)
	@mkdir -p $(PACKAGE_DIR)
	@for target in $(TARGETS); do \
		os=$${target%%_*}; arch=$${target##*_}; \
		if [ "$$os" = "windows" ]; then \
			binary=$(BUILD_DIR)/rmqctl-$$os-$$arch.exe; \
		else \
			binary=$(BUILD_DIR)/rmqctl-$$os-$$arch; \
		fi; \
		bash scripts/package-release.sh "$$binary" "$$os" "$$arch" "$(VERSION)" "$(LEGAL_DIR)" "$(PACKAGE_DIR)"; \
	done

checksum: package
	@cd $(PACKAGE_DIR) && cat *.tar.gz.sha256 *.zip.sha256 | sort -k2 > rmqctl_$(VERSION)_checksums.txt

release: checksum

completion: build
	@mkdir -p $(BUILD_DIR)/completion
	$(BUILD_DIR)/rmqctl completion bash       > $(BUILD_DIR)/completion/rmqctl.bash
	$(BUILD_DIR)/rmqctl completion zsh        > $(BUILD_DIR)/completion/_rmqctl
	$(BUILD_DIR)/rmqctl completion fish       > $(BUILD_DIR)/completion/rmqctl.fish
	$(BUILD_DIR)/rmqctl completion powershell > $(BUILD_DIR)/completion/rmqctl.ps1

install: build
	@mkdir -p $(DESTDIR)/usr/local/bin
	cp $(BUILD_DIR)/rmqctl $(DESTDIR)/usr/local/bin/rmqctl

clean:
	rm -rf $(BUILD_DIR)
