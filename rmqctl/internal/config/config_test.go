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
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestSave(t *testing.T) {
	t.Run("round trip preserves config", func(t *testing.T) {
		directory := t.TempDir()
		if err := os.Chmod(directory, 0o700); err != nil {
			t.Fatal(err)
		}
		path := filepath.Join(directory, "config.yaml")
		want := Config{
			CurrentContext: "prod",
			Contexts: map[string]Context{
				"prod": {
					Server:  "https://studio.example.com",
					Cluster: "instance-prod",
					Credential: CredentialRef{
						AccessKeyRef: "env:RMQ_PROD_AK",
						SecretKeyRef: "env:RMQ_PROD_SK",
					},
				},
			},
		}
		store := NewStore()
		if err := store.Save(path, want); err != nil {
			t.Fatal(err)
		}
		got, err := store.Load(path)
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(got, want) {
			t.Fatalf("round trip mismatch:\ngot  %#v\nwant %#v", got, want)
		}
	})
}

func TestResolveCredential(t *testing.T) {
	t.Run("resolves env references with trim", func(t *testing.T) {
		values := map[string]string{"RMQ_AK": "admin-ak", "RMQ_SK": " secret with spaces "}
		credential, err := ResolveCredential(CredentialRef{
			AccessKeyRef: "env:RMQ_AK",
			SecretKeyRef: "env:RMQ_SK",
		}, func(name string) string { return values[name] })
		if err != nil {
			t.Fatal(err)
		}
		if credential.AccessKey != "admin-ak" || credential.SecretKey != "secret with spaces" {
			t.Fatalf("credential = %#v", credential)
		}
	})

}
