// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Collect license texts driven by the target binary's Go buildinfo; do not treat modules in go.mod that are not linked in as distributed dependencies.
package main

import (
	"bytes"
	"crypto/sha256"
	"debug/buildinfo"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

var legalName = regexp.MustCompile(`(?i)^(licen[sc]e|notice|copying|copyright|patents|authors)([._-].*)?$`)

type component struct {
	Name    string   `json:"name"`
	Version string   `json:"version"`
	Sum     string   `json:"sum,omitempty"`
	Files   []string `json:"files"`
}

type inventory struct {
	BinarySHA256 string            `json:"binarySha256"`
	GOOS         string            `json:"goos"`
	GOARCH       string            `json:"goarch"`
	Components   []component       `json:"components"`
	Files        map[string]string `json:"files"`
}

func must(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, "license gate:", err)
		os.Exit(1)
	}
}

func read(path string) []byte {
	data, err := os.ReadFile(path)
	must(err)
	if len(bytes.TrimSpace(data)) == 0 {
		must(fmt.Errorf("empty license material: %s", path))
	}
	return data
}

func digest(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

func command(goCommand, cwd string, args ...string) []byte {
	cmd := exec.Command(goCommand, args...)
	cmd.Dir = cwd
	cmd.Stderr = os.Stderr
	out, err := cmd.Output()
	must(err)
	return out
}

func main() {
	binary := flag.String("binary", "", "target Go binary")
	output := flag.String("output", "", "license output directory")
	root := flag.String("root", "..", "repository root")
	goCommand := flag.String("go", "go", "Go command used for the build")
	check := flag.Bool("check", false, "recompute and verify existing materials byte by byte without writing files")
	expectedOS := flag.String("os", "", "expected target operating system")
	expectedArch := flag.String("arch", "", "expected target architecture")
	flag.Parse()
	if *binary == "" || *output == "" {
		flag.Usage()
		os.Exit(2)
	}
	info, err := buildinfo.ReadFile(*binary)
	must(err)
	const ownModule = "github.com/apache/rocketmq-dashboard/rmqctl"
	isOwn := info.Main.Path == ownModule
	for _, dep := range info.Deps {
		if dep.Path == ownModule && dep.Version == "(devel)" && dep.Replace == nil {
			isOwn = true
		}
	}
	if !isOwn || len(info.Deps) == 0 {
		must(fmt.Errorf("not an rmqctl binary with module info: %s", *binary))
	}
	cwd := filepath.Join(*root, "rmqctl")
	var env struct{ GOROOT, GOVERSION string }
	must(json.Unmarshal(command(*goCommand, cwd, "env", "-json", "GOROOT", "GOVERSION"), &env))
	if info.GoVersion != env.GOVERSION {
		must(fmt.Errorf("Go license version mismatch: binary=%s toolchain=%s", info.GoVersion, env.GOVERSION))
	}
	inv := inventory{BinarySHA256: digest(read(*binary)), Files: map[string]string{}}
	for _, s := range info.Settings {
		switch s.Key {
		case "GOOS":
			inv.GOOS = s.Value
		case "GOARCH":
			inv.GOARCH = s.Value
		case "CGO_ENABLED":
			if s.Value != "0" {
				must(fmt.Errorf("binaries with C dynamic dependencies are not supported yet; their licenses need separate review"))
			}
		}
	}
	if inv.GOOS == "" || inv.GOARCH == "" {
		must(fmt.Errorf("binary is missing target platform info"))
	}
	if (*expectedOS != "" && inv.GOOS != *expectedOS) || (*expectedArch != "" && inv.GOARCH != *expectedArch) {
		must(fmt.Errorf("binary target does not match the package name: %s/%s", inv.GOOS, inv.GOARCH))
	}
	// The standard library also contains vendored code; keep only the ancestor licenses of packages actually used on the target platform.
	stdDirs := map[string]bool{env.GOROOT: true}
	args := []string{"list", "-deps", "-json", "-mod=readonly"}
	for _, setting := range info.Settings {
		if setting.Key == "-tags" {
			args = append(args, "-tags="+setting.Value)
		}
	}
	args = append(args, "main.go")
	cmd := exec.Command(*goCommand, args...)
	cmd.Dir = cwd
	cmd.Env = append(os.Environ(), "GOOS="+inv.GOOS, "GOARCH="+inv.GOARCH, "CGO_ENABLED=0", "GOFLAGS=")
	cmd.Stderr = os.Stderr
	packages, err := cmd.Output()
	must(err)
	decoder := json.NewDecoder(bytes.NewReader(packages))
	for {
		var pkg struct {
			Standard bool
			Dir      string
		}
		err := decoder.Decode(&pkg)
		if err == io.EOF {
			break
		}
		must(err)
		if pkg.Standard {
			for dir := pkg.Dir; dir != env.GOROOT && strings.HasPrefix(dir, env.GOROOT+string(filepath.Separator)); dir = filepath.Dir(dir) {
				stdDirs[dir] = true
			}
		}
	}
	files := map[string][]byte{}
	base := func(name string) string {
		text := string(read(filepath.Join(*root, name)))
		return strings.Split(text, "\nThird-party source materials\n")[0]
	}
	license, notice := base("LICENSE"), base("NOTICE")
	collect := func(name, version, sum, dir string, toolchain bool) {
		c := component{Name: name, Version: version, Sum: sum}
		prefix := "licenses/" + name + "@" + version + "/"
		foundLicense := false
		must(filepath.WalkDir(dir, func(path string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			rel, err := filepath.Rel(dir, path)
			if err != nil {
				return err
			}
			if entry.IsDir() {
				if entry.Name() == ".git" || entry.Name() == "node_modules" || entry.Name() == "testdata" {
					return filepath.SkipDir
				}
				if toolchain && !stdDirs[path] {
					return filepath.SkipDir
				}
				return nil
			}
			if !legalName.MatchString(entry.Name()) || strings.HasSuffix(entry.Name(), ".go") {
				return nil
			}
			if entry.Type()&os.ModeSymlink != 0 {
				return fmt.Errorf("symlinked license not accepted: %s", path)
			}
			data := read(path)
			lower := strings.ToLower(entry.Name())
			if strings.HasPrefix(lower, "licen") || strings.HasPrefix(lower, "copying") {
				if len(data) < 300 {
					return fmt.Errorf("license too short; the complete text must be verified: %s", path)
				}
				foundLicense = true
			}
			key := prefix + filepath.ToSlash(rel) + ".txt"
			files[key] = data
			c.Files = append(c.Files, key)
			if strings.HasPrefix(lower, "notice") {
				notice += "\n--- " + name + " " + version + " / " + filepath.ToSlash(rel) + " ---\n" + string(data) + "\n"
			}
			return nil
		}))
		if !foundLicense {
			must(fmt.Errorf("%s@%s is missing the complete upstream LICENSE/COPYING", name, version))
		}
		sort.Strings(c.Files)
		inv.Components = append(inv.Components, c)
		license += "\n" + name + " " + version + ": complete upstream license, copyright and additional terms under legal/" + prefix + "\n"
	}
	collect("go", info.GoVersion, "", env.GOROOT, true)
	sort.Slice(info.Deps, func(i, j int) bool { return info.Deps[i].Path < info.Deps[j].Path })
	for _, dep := range info.Deps {
		if dep.Path == ownModule && dep.Version == "(devel)" && dep.Replace == nil {
			continue
		}
		if dep.Replace != nil {
			must(fmt.Errorf("module replacement requires manual source review: %s", dep.Path))
		}
		var module struct {
			Path, Version, Dir, Sum string
			Error                   *struct{ Err string }
		}
		must(json.Unmarshal(command(*goCommand, cwd, "mod", "download", "-json", dep.Path+"@"+dep.Version), &module))
		if module.Dir == "" || module.Path != dep.Path || module.Version != dep.Version || module.Sum != dep.Sum {
			must(fmt.Errorf("module source/checksum mismatch: %s@%s", dep.Path, dep.Version))
		}
		collect(dep.Path, dep.Version, dep.Sum, module.Dir, false)
	}
	files["LICENSE"] = []byte(license)
	files["NOTICE"] = []byte(notice)
	for name, data := range files {
		inv.Files[name] = digest(data)
	}
	manifest, err := json.MarshalIndent(inv, "", "  ")
	must(err)
	files["manifest.json"] = append(manifest, '\n')
	if *check {
		seen := 0
		must(filepath.WalkDir(*output, func(path string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			if entry.IsDir() {
				return nil
			}
			rel, err := filepath.Rel(*output, path)
			if err != nil {
				return err
			}
			want, ok := files[filepath.ToSlash(rel)]
			if !ok || entry.Type()&os.ModeSymlink != 0 || !bytes.Equal(read(path), want) {
				return fmt.Errorf("material missing, stale or modified: %s", path)
			}
			seen++
			return nil
		}))
		if seen != len(files) {
			must(fmt.Errorf("license file count mismatch: got %d, expected %d", seen, len(files)))
		}
	} else {
		// Do not delete unknown files; a later check rejects leftover materials from a previous build.
		for name, data := range files {
			path := filepath.Join(*output, filepath.FromSlash(name))
			must(os.MkdirAll(filepath.Dir(path), 0755))
			must(os.WriteFile(path, data, 0644))
		}
	}
	fmt.Printf("license materials: %s/%s, %d actual components, %d files\n", inv.GOOS, inv.GOARCH, len(inv.Components), len(files))
}
