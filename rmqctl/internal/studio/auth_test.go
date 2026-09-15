/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package studio

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"testing"
	"time"
)

type hmacGoldenVector struct {
	Name             string `json:"name"`
	AccessKey        string `json:"accessKey"`
	SecretKey        string `json:"secretKey"`
	Cluster          string `json:"cluster"`
	Timestamp        string `json:"timestamp"`
	Method           string `json:"method"`
	Path             string `json:"path"`
	Body             string `json:"body"`
	CanonicalRequest string `json:"canonicalRequest"`
	Signature        string `json:"signature"`
}

func TestSigningRoundTripperMatchesGoldenVectors(t *testing.T) {
	for _, vector := range loadHMACGoldenVectors(t) {
		t.Run(vector.Name, func(t *testing.T) {
			canonical := canonicalRequest(vector.AccessKey, vector.Cluster, vector.Timestamp,
				vector.Method, vector.Path)
			if canonical != vector.CanonicalRequest {
				t.Fatalf("canonical request mismatch:\ngot:\n%s\nwant:\n%s", canonical, vector.CanonicalRequest)
			}
			mac := hmac.New(sha256.New, []byte(vector.SecretKey))
			_, _ = mac.Write([]byte(canonical))
			if signature := hex.EncodeToString(mac.Sum(nil)); signature != vector.Signature {
				t.Fatalf("signature = %s, want %s", signature, vector.Signature)
			}

			var bodyReader io.Reader
			if vector.Body != "" {
				bodyReader = strings.NewReader(vector.Body)
			}
			request, err := http.NewRequest(
				vector.Method,
				"https://studio.example.com"+vector.Path,
				bodyReader)
			if err != nil {
				t.Fatal(err)
			}
			transport := AuthTransport{
				now: func() time.Time {
					milliseconds, err := strconv.ParseInt(vector.Timestamp, 10, 64)
					if err != nil {
						t.Fatal(err)
					}
					return time.UnixMilli(milliseconds)
				},
				target: Target{
					Server:     "https://studio.example.com",
					Cluster:    vector.Cluster,
					Credential: Credential{AccessKey: vector.AccessKey, SecretKey: vector.SecretKey},
				},
				base: roundTripFunc(func(signed *http.Request) (*http.Response, error) {
					wantAuthorization := HMACAlgorithm + " Credential=" + rfc3986Encode(vector.AccessKey) +
						", Signature=" + vector.Signature
					if signed.Header.Get("Authorization") != wantAuthorization {
						t.Fatalf("Authorization = %q, want %q", signed.Header.Get("Authorization"), wantAuthorization)
					}
					if signed.Header.Get(HeaderInstance) != vector.Cluster {
						t.Fatalf("signed headers = %#v", signed.Header)
					}
					if signed.Header.Get(HeaderTimestamp) != vector.Timestamp {
						t.Fatalf("timestamp = %q, want %q", signed.Header.Get(HeaderTimestamp), vector.Timestamp)
					}
					if vector.Body != "" {
						actualBody, err := io.ReadAll(signed.Body)
						if err != nil {
							return nil, err
						}
						if string(actualBody) != vector.Body {
							t.Fatalf("body = %q, want %q", actualBody, vector.Body)
						}
					}
					return &http.Response{
						StatusCode: http.StatusNoContent,
						Body:       http.NoBody,
						Header:     make(http.Header),
						Request:    signed,
					}, nil
				}),
			}
			if _, err := transport.RoundTrip(request); err != nil {
				t.Fatal(err)
			}
		})
	}
}

func loadHMACGoldenVectors(t *testing.T) []hmacGoldenVector {
	t.Helper()
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("resolve test source path")
	}
	path := filepath.Join(filepath.Dir(source), "..", "..", "..", "testdata", "auth-hmac-vectors.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read required HMAC golden vectors: %v", err)
	}
	var vectors []hmacGoldenVector
	if err := json.Unmarshal(data, &vectors); err != nil {
		t.Fatal(err)
	}
	if len(vectors) == 0 {
		t.Fatal("no HMAC golden vectors")
	}
	return vectors
}

type unreadableSigningBody struct {
	t *testing.T
}

func (body unreadableSigningBody) Read([]byte) (int, error) {
	body.t.Fatal("signing must not read the body")
	return 0, io.EOF
}

func (body unreadableSigningBody) Close() error {
	body.t.Fatal("signing must leave the body open for the base transport")
	return nil
}
