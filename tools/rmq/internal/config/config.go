/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Package config provides explicit, file-backed configuration for rmqctl.
package config

import (
	"errors"
	"fmt"
	"io"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"

	"go.yaml.in/yaml/v3"
)

const apiVersion = "rmq.apache.org/v1alpha1"

var tokenEnvPattern = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*$`)

type stagedConfig struct {
	path string
	info os.FileInfo
}

// Config is the on-disk rmqctl configuration.
type Config struct {
	APIVersion     string             `yaml:"apiVersion" json:"apiVersion"`
	CurrentContext string             `yaml:"currentContext" json:"currentContext"`
	Contexts       map[string]Context `yaml:"contexts" json:"contexts"`
}

// Context identifies one RocketMQ Dashboard endpoint and its display settings.
// TokenEnv names an environment variable; token values are never stored here.
type Context struct {
	Server   string `yaml:"server" json:"server"`
	Cluster  string `yaml:"cluster,omitempty" json:"cluster,omitempty"`
	Output   string `yaml:"output" json:"output"`
	TokenEnv string `yaml:"tokenEnv,omitempty" json:"tokenEnv,omitempty"`
	CAFile   string `yaml:"caFile,omitempty" json:"caFile,omitempty"`
}

// Default returns the local development configuration.
func Default() Config {
	return Config{
		APIVersion:     apiVersion,
		CurrentContext: "default",
		Contexts: map[string]Context{
			"default": {
				Server:   "http://127.0.0.1:8888",
				Output:   "table",
				TokenEnv: "RMQCTL_TOKEN",
			},
		},
	}
}

// DefaultPath returns the default per-user configuration path.
func DefaultPath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("resolve user home directory: %w", err)
	}
	return filepath.Join(home, ".rmqctl", "config.yaml"), nil
}

// Load decodes and validates exactly one YAML document from path.
func Load(path string) (Config, error) {
	file, err := os.Open(path)
	if err != nil {
		return Config{}, fmt.Errorf("open config: %w", err)
	}
	defer file.Close()

	decoder := yaml.NewDecoder(file)
	decoder.KnownFields(true)

	var cfg Config
	if err := decoder.Decode(&cfg); err != nil {
		return Config{}, fmt.Errorf("decode config: %w", err)
	}

	var extra yaml.Node
	if err := decoder.Decode(&extra); !errors.Is(err, io.EOF) {
		if err != nil {
			return Config{}, fmt.Errorf("decode trailing config document: %w", err)
		}
		return Config{}, errors.New("config must contain exactly one YAML document")
	}

	if err := Validate(cfg); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

// Save validates and writes cfg to path. Existing files require force.
func Save(path string, cfg Config, force bool) error {
	if err := Validate(cfg); err != nil {
		return err
	}

	contents, err := yaml.Marshal(cfg)
	if err != nil {
		return fmt.Errorf("encode config: %w", err)
	}

	parent := filepath.Dir(path)
	if err := ensureParentDirectory(parent); err != nil {
		return err
	}

	info, err := os.Lstat(path)
	switch {
	case err == nil:
		if !info.Mode().IsRegular() {
			return errors.New("config target is not a regular file")
		}
		if !force {
			return errors.New("config already exists")
		}
	case os.IsNotExist(err):
		if force {
			return writeAtomic(path, contents)
		}
		return writeExclusive(path, contents)
	default:
		return fmt.Errorf("inspect config target: %w", err)
	}

	return writeAtomic(path, contents)
}

func ensureParentDirectory(parent string) error {
	info, err := os.Stat(parent)
	if err == nil {
		return validateParentDirectory(info)
	}
	if !os.IsNotExist(err) {
		return fmt.Errorf("inspect config directory: %w", err)
	}

	if err := os.MkdirAll(parent, 0700); err != nil {
		return fmt.Errorf("create config directory: %w", err)
	}
	info, err = os.Stat(parent)
	if err != nil {
		return fmt.Errorf("inspect config directory: %w", err)
	}
	return validateParentDirectory(info)
}

func validateParentDirectory(info os.FileInfo) error {
	if !info.IsDir() {
		return errors.New("config parent is not a directory")
	}
	if runtime.GOOS != "windows" &&
		info.Mode().Perm()&0022 != 0 &&
		info.Mode()&os.ModeSticky == 0 {
		return errors.New("config parent directory is group/world-writable without a sticky bit")
	}
	return nil
}

func writeExclusive(path string, contents []byte) (returnErr error) {
	staged, err := writeTemp(path, contents)
	if err != nil {
		return err
	}
	defer func() {
		returnErr = errors.Join(returnErr, removeTemporaryConfig(staged.path))
	}()

	if err := os.Link(staged.path, path); err != nil {
		return fmt.Errorf("publish config without replacing existing target: %w", err)
	}
	if err := verifyPublishedConfig(staged.info, path); err != nil {
		return errors.Join(err, removeInvalidPublication(path))
	}
	return nil
}

func writeAtomic(path string, contents []byte) (returnErr error) {
	staged, err := writeTemp(path, contents)
	if err != nil {
		return err
	}
	defer func() {
		returnErr = errors.Join(returnErr, removeTemporaryConfig(staged.path))
	}()

	if err := os.Rename(staged.path, path); err != nil {
		return fmt.Errorf("replace config: %w", err)
	}
	if err := verifyPublishedConfig(staged.info, path); err != nil {
		return errors.Join(err, removeInvalidPublication(path))
	}
	return nil
}

func writeTemp(path string, contents []byte) (_ stagedConfig, returnErr error) {
	parent := filepath.Dir(path)
	file, err := os.CreateTemp(parent, "."+filepath.Base(path)+".tmp-*")
	if err != nil {
		return stagedConfig{}, fmt.Errorf("create temporary config: %w", err)
	}
	tempPath := file.Name()
	closed := false
	defer func() {
		if returnErr != nil {
			if !closed {
				if err := file.Close(); err != nil {
					returnErr = errors.Join(returnErr, fmt.Errorf("close temporary config during cleanup: %w", err))
				}
			}
			returnErr = errors.Join(returnErr, removeTemporaryConfig(tempPath))
		}
	}()

	if err := file.Chmod(0600); err != nil {
		return stagedConfig{}, fmt.Errorf("secure temporary config: %w", err)
	}
	if _, err := file.Write(contents); err != nil {
		return stagedConfig{}, fmt.Errorf("write temporary config: %w", err)
	}
	if err := file.Sync(); err != nil {
		return stagedConfig{}, fmt.Errorf("sync temporary config: %w", err)
	}
	info, err := file.Stat()
	if err != nil {
		return stagedConfig{}, fmt.Errorf("inspect temporary config: %w", err)
	}
	err = file.Close()
	closed = true
	if err != nil {
		return stagedConfig{}, fmt.Errorf("close temporary config: %w", err)
	}
	return stagedConfig{path: tempPath, info: info}, nil
}

func verifyPublishedConfig(expected os.FileInfo, path string) error {
	actual, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("config publication integrity check failed: inspect target: %w", err)
	}
	if !os.SameFile(expected, actual) {
		return errors.New("config publication integrity check failed: target identity does not match staged file")
	}
	return nil
}

func removeTemporaryConfig(path string) error {
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("remove temporary config: %w", err)
	}
	return nil
}

func removeInvalidPublication(path string) error {
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("remove invalid published config: %w", err)
	}
	return nil
}

// Validate checks configuration structure and endpoint safety.
func Validate(cfg Config) error {
	if cfg.APIVersion != apiVersion {
		return fmt.Errorf("apiVersion must be %q", apiVersion)
	}
	if len(cfg.Contexts) == 0 {
		return errors.New("contexts must not be empty")
	}
	if _, ok := cfg.Contexts[cfg.CurrentContext]; !ok {
		return errors.New("currentContext must name an existing context")
	}

	for name, ctx := range cfg.Contexts {
		if name == "" {
			return errors.New("context name must not be empty")
		}
		if err := validateContext(ctx); err != nil {
			return fmt.Errorf("context %q: %w", name, err)
		}
	}
	return nil
}

func validateContext(ctx Context) error {
	switch ctx.Output {
	case "table", "json", "yaml":
	default:
		return errors.New("output must be table, json, or yaml")
	}
	if ctx.TokenEnv != "" && !tokenEnvPattern.MatchString(ctx.TokenEnv) {
		return errors.New("tokenEnv must be a POSIX environment variable name")
	}

	endpoint, err := url.Parse(ctx.Server)
	if err != nil {
		return fmt.Errorf("server must be a valid absolute URL: %w", err)
	}
	if !endpoint.IsAbs() || endpoint.Host == "" || endpoint.Hostname() == "" || endpoint.Opaque != "" {
		return errors.New("server must be an absolute URL with a host")
	}
	if endpoint.Scheme != "http" && endpoint.Scheme != "https" {
		return errors.New("server scheme must be http or https")
	}
	if endpoint.User != nil {
		return errors.New("server must not contain user information")
	}
	if strings.Contains(ctx.Server, "#") {
		return errors.New("server must not contain a fragment")
	}
	if endpoint.RawQuery != "" || endpoint.ForceQuery {
		return errors.New("server must not contain a query")
	}
	if endpoint.Path != "" && endpoint.Path != "/" {
		return errors.New("server path must be empty or root")
	}
	if strings.HasSuffix(endpoint.Host, ":") {
		return errors.New("server port must not be empty")
	}
	if port := endpoint.Port(); port != "" {
		number, err := strconv.ParseUint(port, 10, 16)
		if err != nil || number == 0 {
			return errors.New("server port must be an integer from 1 through 65535")
		}
	}

	if endpoint.Scheme == "http" && !isLoopbackHost(endpoint.Hostname()) {
		return errors.New("http server host must be localhost or a loopback IP address")
	}
	return nil
}

func isLoopbackHost(host string) bool {
	if strings.EqualFold(host, "localhost") {
		return true
	}
	ip := net.ParseIP(host)
	if ip == nil {
		return false
	}
	if strings.Contains(host, ":") {
		return ip.Equal(net.IPv6loopback)
	}
	ipv4 := ip.To4()
	return ipv4 != nil && ipv4[0] == 127
}

// SetContext validates a context update before applying it to cfg.
func SetContext(cfg *Config, name string, ctx Context) error {
	if cfg == nil {
		return errors.New("config must not be nil")
	}
	candidate := clone(*cfg)
	if candidate.Contexts == nil {
		candidate.Contexts = make(map[string]Context)
	}
	candidate.Contexts[name] = ctx
	if err := Validate(candidate); err != nil {
		return err
	}
	*cfg = candidate
	return nil
}

// UseContext makes name current after checking the resulting configuration.
func UseContext(cfg *Config, name string) error {
	if cfg == nil {
		return errors.New("config must not be nil")
	}
	if _, ok := cfg.Contexts[name]; !ok {
		return fmt.Errorf("context %q does not exist", name)
	}
	candidate := clone(*cfg)
	candidate.CurrentContext = name
	if err := Validate(candidate); err != nil {
		return err
	}
	*cfg = candidate
	return nil
}

// Current validates cfg and returns its current context.
func Current(cfg Config) (Context, error) {
	if err := Validate(cfg); err != nil {
		return Context{}, err
	}
	return cfg.Contexts[cfg.CurrentContext], nil
}

func clone(cfg Config) Config {
	copy := cfg
	if cfg.Contexts != nil {
		copy.Contexts = make(map[string]Context, len(cfg.Contexts))
		for name, ctx := range cfg.Contexts {
			copy.Contexts[name] = ctx
		}
	}
	return copy
}
