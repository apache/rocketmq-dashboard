/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package config

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"unicode/utf8"

	"gopkg.in/yaml.v3"
)

var environmentName = regexp.MustCompile(`^[A-Za-z_][A-Za-z0-9_]*$`)

type Config struct {
	CurrentContext string             `yaml:"currentContext,omitempty" json:"currentContext,omitempty"`
	Contexts       map[string]Context `yaml:"contexts,omitempty" json:"contexts,omitempty"`
}

type Context struct {
	Server     string        `yaml:"server" json:"server"`
	Cluster    string        `yaml:"cluster" json:"cluster"`
	Credential CredentialRef `yaml:"credential" json:"credential"`
}

type CredentialRef struct {
	AccessKeyRef string `yaml:"accessKeyRef" json:"accessKeyRef"`
	SecretKeyRef string `yaml:"secretKeyRef" json:"secretKeyRef"`
}

// Credential exists only in process memory. It is never marshalled into config.
type Credential struct {
	AccessKey string
	SecretKey string
}

type Store struct {
	Getenv  func(string) string
	HomeDir func() (string, error)
}

func NewStore() Store {
	return Store{Getenv: os.Getenv, HomeDir: os.UserHomeDir}
}

func (s Store) Path(explicit string) (string, error) {
	if explicit != "" {
		return explicit, nil
	}
	if envPath := s.Getenv("RMQCTL_CONFIG"); envPath != "" {
		return envPath, nil
	}
	home, err := s.HomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".rmqctl", "config.yaml"), nil
}

func (s Store) Load(path string) (Config, error) {
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return EmptyConfig(), nil
	}
	if err != nil {
		return Config{}, err
	}
	if err := checkFilePermissions(path); err != nil {
		return Config{}, err
	}
	if len(bytes.TrimSpace(data)) == 0 {
		return EmptyConfig(), nil
	}
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	var cfg Config
	if err := decoder.Decode(&cfg); err != nil {
		return Config{}, fmt.Errorf("parse config %s: %w", path, err)
	}
	var trailing any
	if err := decoder.Decode(&trailing); err == nil {
		return Config{}, fmt.Errorf("parse config %s: multiple YAML documents are not allowed", path)
	} else if !errors.Is(err, io.EOF) {
		return Config{}, fmt.Errorf("parse config %s: %w", path, err)
	}
	Normalize(&cfg)
	if err := Validate(cfg); err != nil {
		return Config{}, fmt.Errorf("validate config %s: %w", path, err)
	}
	return cfg, nil
}

func (s Store) Save(path string, cfg Config) error {
	Normalize(&cfg)
	if err := Validate(cfg); err != nil {
		return fmt.Errorf("validate config: %w", err)
	}
	data, err := yaml.Marshal(cfg)
	if err != nil {
		return fmt.Errorf("marshal config: %w", err)
	}
	directory := filepath.Dir(path)
	if err := ensureConfigDirectory(directory); err != nil {
		return err
	}
	temporary, err := os.CreateTemp(directory, ".rmqctl-config-*")
	if err != nil {
		return err
	}
	temporaryPath := temporary.Name()
	defer func() { _ = os.Remove(temporaryPath) }()
	if _, err := temporary.Write(data); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Sync(); err != nil {
		_ = temporary.Close()
		return err
	}
	if err := temporary.Close(); err != nil {
		return err
	}
	if err := os.Rename(temporaryPath, path); err != nil {
		return err
	}
	return secureConfigFile(path)
}

func EmptyConfig() Config {
	return Config{Contexts: map[string]Context{}}
}

func Normalize(cfg *Config) {
	if cfg.Contexts == nil {
		cfg.Contexts = map[string]Context{}
	}
}

func Validate(cfg Config) error {
	for name, context := range cfg.Contexts {
		if strings.TrimSpace(name) == "" {
			return errors.New("context name must not be empty")
		}
		if err := ValidateContext(context); err != nil {
			return fmt.Errorf("context %q: %w", name, err)
		}
	}
	if cfg.CurrentContext != "" {
		if _, ok := cfg.Contexts[cfg.CurrentContext]; !ok {
			return fmt.Errorf("currentContext %q does not exist", cfg.CurrentContext)
		}
	}
	return nil
}

func ValidateContext(context Context) error {
	required := []struct {
		name  string
		value string
	}{
		{"server", context.Server},
		{"cluster", context.Cluster},
		{"credential.accessKeyRef", context.Credential.AccessKeyRef},
		{"credential.secretKeyRef", context.Credential.SecretKeyRef},
	}
	for _, field := range required {
		if strings.TrimSpace(field.value) == "" {
			return fmt.Errorf("%s is required", field.name)
		}
	}
	if _, err := EnvironmentReference(context.Credential.AccessKeyRef); err != nil {
		return fmt.Errorf("credential.accessKeyRef: %w", err)
	}
	if _, err := EnvironmentReference(context.Credential.SecretKeyRef); err != nil {
		return fmt.Errorf("credential.secretKeyRef: %w", err)
	}
	return nil
}

func ContextName(explicit string, cfg Config) (string, error) {
	name := explicit
	if name == "" {
		name = cfg.CurrentContext
	}
	if name == "" {
		return "", errors.New("no current context is selected; use --context or 'config use-context'")
	}
	if _, ok := cfg.Contexts[name]; !ok {
		return "", fmt.Errorf("context %q does not exist", name)
	}
	return name, nil
}

func ResolveCredential(reference CredentialRef, getenv func(string) string) (Credential, error) {
	accessEnvironment, err := EnvironmentReference(reference.AccessKeyRef)
	if err != nil {
		return Credential{}, fmt.Errorf("accessKeyRef: %w", err)
	}
	secretEnvironment, err := EnvironmentReference(reference.SecretKeyRef)
	if err != nil {
		return Credential{}, fmt.Errorf("secretKeyRef: %w", err)
	}
	accessKey := strings.TrimSpace(getenv(accessEnvironment))
	secretKey := strings.TrimSpace(getenv(secretEnvironment))
	if accessKey == "" {
		return Credential{}, fmt.Errorf("environment variable %s is empty", accessEnvironment)
	}
	if secretKey == "" {
		return Credential{}, fmt.Errorf("environment variable %s is empty", secretEnvironment)
	}
	if !utf8.ValidString(accessKey) || !utf8.ValidString(secretKey) {
		return Credential{}, fmt.Errorf("credential environment variables must contain valid UTF-8")
	}
	return Credential{AccessKey: accessKey, SecretKey: secretKey}, nil
}

func EnvironmentReference(reference string) (string, error) {
	name, ok := strings.CutPrefix(reference, "env:")
	if !ok || !environmentName.MatchString(name) {
		return "", fmt.Errorf("%q must be an env:NAME reference", reference)
	}
	return name, nil
}
