GO ?= go
VERSION ?= dev
BUILD_DIR ?= $(CURDIR)/bin
PACKAGE_DIR ?= $(BUILD_DIR)/release
CHECKSUM ?= shasum -a 256
TARGETS := darwin-amd64 darwin-arm64 linux-amd64 linux-arm64 windows-amd64
LDFLAGS := -s -w -X github.com/apache/rocketmq-dashboard/rmqctl/cmd.CLIVersion=$(VERSION)

.PHONY: build build-all package checksum release clean

build:
	@mkdir -p $(BUILD_DIR)
	CGO_ENABLED=0 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl main.go

build-all:
	@mkdir -p $(BUILD_DIR)
	CGO_ENABLED=0 GOOS=darwin GOARCH=amd64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-darwin-amd64 main.go
	CGO_ENABLED=0 GOOS=darwin GOARCH=arm64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-darwin-arm64 main.go
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-linux-amd64 main.go
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-linux-arm64 main.go
	CGO_ENABLED=0 GOOS=windows GOARCH=amd64 $(GO) build -ldflags "$(LDFLAGS)" -o $(BUILD_DIR)/rmqctl-windows-amd64.exe main.go

package: build-all
	@rm -rf $(PACKAGE_DIR)
	@mkdir -p $(PACKAGE_DIR)/staging
	@for target in $(TARGETS); do \
		name=rmqctl-$(VERSION)-$$target; \
		dir=$(PACKAGE_DIR)/staging/$$name; \
		mkdir -p $$dir; \
		if [ "$$target" = "windows-amd64" ]; then \
			cp $(BUILD_DIR)/rmqctl-$$target.exe $$dir/rmqctl.exe; \
		else \
			cp $(BUILD_DIR)/rmqctl-$$target $$dir/rmqctl; \
		fi; \
		cp ../LICENSE ../NOTICE $$dir/; \
		cp ../docs/studio/rip3-rmqctl-user-guide.md $$dir/README.md; \
		tar -C $(PACKAGE_DIR)/staging -czf $(PACKAGE_DIR)/$$name.tar.gz $$name; \
	done
	@rm -rf $(PACKAGE_DIR)/staging

checksum: package
	@cd $(PACKAGE_DIR) && $(CHECKSUM) *.tar.gz > checksums.txt

release: checksum

clean:
	rm -rf $(BUILD_DIR)
