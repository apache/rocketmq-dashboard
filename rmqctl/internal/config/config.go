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
package config

import (
	"errors"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

const DefaultProfile = "default"

type Config struct {
	CurrentProfile string             `json:"currentProfile"`
	Profiles       map[string]Profile `json:"profiles"`
}

type Profile struct {
	Server   string `json:"server,omitempty"`
	TokenRef string `json:"tokenRef,omitempty"`
	Cluster  string `json:"cluster,omitempty"`
}

type Store struct {
	Getenv    func(string) string
	HomeDir   func() (string, error)
	ReadFile  func(string) ([]byte, error)
	WriteFile func(string, []byte, os.FileMode) error
	MkdirAll  func(string, os.FileMode) error
}

func NewStore() Store {
	return Store{
		Getenv:    os.Getenv,
		HomeDir:   os.UserHomeDir,
		ReadFile:  os.ReadFile,
		WriteFile: os.WriteFile,
		MkdirAll:  os.MkdirAll,
	}
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
	return filepath.Join(home, ".rocketmq-studio", "config.yaml"), nil
}

func (s Store) Load(path string) (Config, error) {
	data, err := s.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return DefaultConfig(), nil
	}
	if err != nil {
		return Config{}, err
	}
	cfg := DefaultConfig()
	current := ""
	for _, line := range strings.Split(string(data), "\n") {
		raw := strings.TrimRight(line, " \t\r")
		trimmed := strings.TrimSpace(raw)
		if trimmed == "" || strings.HasPrefix(trimmed, "#") || trimmed == "profiles:" {
			continue
		}
		if strings.HasPrefix(trimmed, "currentProfile:") {
			cfg.CurrentProfile = strings.TrimSpace(strings.TrimPrefix(trimmed, "currentProfile:"))
			continue
		}
		if strings.HasPrefix(raw, "  ") && !strings.HasPrefix(raw, "    ") && strings.HasSuffix(trimmed, ":") {
			current = strings.TrimSuffix(trimmed, ":")
			if _, ok := cfg.Profiles[current]; !ok {
				cfg.Profiles[current] = Profile{}
			}
			continue
		}
		if strings.HasPrefix(raw, "    ") && current != "" {
			key, value, ok := strings.Cut(trimmed, ":")
			if !ok {
				continue
			}
			p := cfg.Profiles[current]
			value = strings.TrimSpace(value)
			switch key {
			case "server":
				p.Server = value
			case "tokenRef":
				p.TokenRef = value
			case "cluster":
				p.Cluster = value
			}
			cfg.Profiles[current] = p
		}
	}
	Normalize(&cfg)
	return cfg, nil
}

func (s Store) Save(path string, cfg Config) error {
	Normalize(&cfg)
	var out strings.Builder
	out.WriteString("currentProfile: ")
	out.WriteString(cfg.CurrentProfile)
	out.WriteString("\nprofiles:\n")
	names := make([]string, 0, len(cfg.Profiles))
	for name := range cfg.Profiles {
		names = append(names, name)
	}
	sort.Strings(names)
	for _, name := range names {
		p := cfg.Profiles[name]
		out.WriteString("  ")
		out.WriteString(name)
		out.WriteString(":\n")
		if p.Server != "" {
			out.WriteString("    server: ")
			out.WriteString(p.Server)
			out.WriteString("\n")
		}
		if p.TokenRef != "" {
			out.WriteString("    tokenRef: ")
			out.WriteString(p.TokenRef)
			out.WriteString("\n")
		}
		if p.Cluster != "" {
			out.WriteString("    cluster: ")
			out.WriteString(p.Cluster)
			out.WriteString("\n")
		}
	}
	if err := s.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return s.WriteFile(path, []byte(out.String()), 0o600)
}

func DefaultConfig() Config {
	return Config{
		CurrentProfile: DefaultProfile,
		Profiles: map[string]Profile{
			DefaultProfile: {},
		},
	}
}

func Normalize(cfg *Config) {
	if cfg.CurrentProfile == "" {
		cfg.CurrentProfile = DefaultProfile
	}
	if cfg.Profiles == nil {
		cfg.Profiles = map[string]Profile{}
	}
	if _, ok := cfg.Profiles[cfg.CurrentProfile]; !ok {
		cfg.Profiles[cfg.CurrentProfile] = Profile{}
	}
}

func ProfileName(explicit string, cfg Config) string {
	if explicit != "" {
		return explicit
	}
	if cfg.CurrentProfile != "" {
		return cfg.CurrentProfile
	}
	return DefaultProfile
}
