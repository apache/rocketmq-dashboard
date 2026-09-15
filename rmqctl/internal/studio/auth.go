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
	"fmt"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	HMACAlgorithm   = "RMQ-HMAC-SHA256"
	HeaderInstance  = "X-RMQ-Cluster"
	HeaderTimestamp = "X-RMQ-Timestamp"
)

type AuthTransport struct {
	base   http.RoundTripper
	target Target
	now    func() time.Time
}

func signedHTTPClient(client *http.Client, target Target) (*http.Client, error) {
	if client == nil {
		return nil, fmt.Errorf("studio: nil HTTP client")
	}
	if err := target.validate(); err != nil {
		return nil, err
	}
	clone := *client
	clone.CheckRedirect = noRedirectPolicy
	base := clone.Transport
	if base == nil {
		base = http.DefaultTransport
	}
	clone.Transport = AuthTransport{base: base, target: target}
	return &clone, nil
}

func (transport AuthTransport) RoundTrip(request *http.Request) (*http.Response, error) {
	clone := request.Clone(request.Context())
	clone.Header = request.Header.Clone()
	clone.Header.Set(HeaderInstance, transport.target.Cluster)
	now := time.Now
	if transport.now != nil {
		now = transport.now
	}
	timestamp := strconv.FormatInt(now().UnixMilli(), 10)
	clone.Header.Set(HeaderTimestamp, timestamp)

	// Include the query string in the signed path so that adding or removing
	// query parameters invalidates the signature.
	signedPath := clone.URL.RequestURI()
	if clone.Method == "" {
		clone.Method = http.MethodGet
	}

	canonical := canonicalRequest(
		transport.target.Credential.AccessKey,
		transport.target.Cluster,
		timestamp,
		clone.Method,
		signedPath,
	)
	mac := hmac.New(sha256.New, []byte(transport.target.Credential.SecretKey))
	_, _ = mac.Write([]byte(canonical))
	signature := hex.EncodeToString(mac.Sum(nil))
	clone.Header.Set("Authorization", HMACAlgorithm+" Credential="+
		rfc3986Encode(transport.target.Credential.AccessKey)+", Signature="+signature)
	return transport.base.RoundTrip(clone)
}

// canonicalRequest builds the string-to-sign for HMAC authentication. The
// method and escaped path/query bind the request target. The body is not read
// or authenticated by this protocol. See docs/mcp-hmac.md.
func canonicalRequest(accessKey, cluster, timestamp, method, path string) string {
	return strings.Join([]string{
		HMACAlgorithm,
		accessKey,
		cluster,
		timestamp,
		method,
		path,
	}, "\n")
}

func rfc3986Encode(value string) string {
	const hexadecimal = "0123456789ABCDEF"
	var encoded strings.Builder
	for _, character := range []byte(value) {
		if character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' ||
			character >= '0' && character <= '9' || strings.ContainsRune("-._~", rune(character)) {
			encoded.WriteByte(character)
			continue
		}
		encoded.WriteByte('%')
		encoded.WriteByte(hexadecimal[character>>4])
		encoded.WriteByte(hexadecimal[character&0x0f])
	}
	return encoded.String()
}

func isLoopbackHost(host string) bool {
	if strings.EqualFold(host, "localhost") {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}
