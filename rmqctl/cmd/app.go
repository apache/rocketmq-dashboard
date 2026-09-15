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
package cmd

import (
	"context"
	"io"
	"net/http"
	"os"
	"os/signal"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
)

var (
	CLIVersion = "1.0.0"
	GitCommit  = ""
	BuildDate  = ""
)

type App struct {
	In      io.Reader
	Out     io.Writer
	Err     io.Writer
	HTTP    *http.Client
	Store   config.Store
	confirm confirmFunc
}

// confirmFunc is the interactive confirmation hook for dangerous operations.
// It receives the tool command path, risk level and target server, writes the
// prompt to out, reads one line from in, and returns nil only when the user
// explicitly approves. A non-nil error aborts the command before any HTTP
// request is sent. Production code leaves App.confirm nil so the default
// TTY-aware implementation in catalog.go is used; tests inject a stub.
type confirmFunc func(in io.Reader, out io.Writer, commandPath, riskLevel, server string) error

func NewApp(out io.Writer, err io.Writer) *App {
	return &App{
		In:    os.Stdin,
		Out:   out,
		Err:   err,
		HTTP:  studio.NewHTTPClient(),
		Store: config.NewStore(),
	}
}

func (a *App) Execute(args []string) int {
	opts := &option{output: output.FormatTable}
	cmd, err := a.newCommand(opts)
	if err != nil {
		a.writeCommandError(opts.output, err)
		return 1
	}
	cmd.SetArgs(args)
	cmd.SetIn(a.In)
	cmd.SetOut(a.Out)
	cmd.SetErr(a.Err)
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()
	if len(args) == 0 {
		_ = cmd.Help()
		return 2
	}
	if err := cmd.ExecuteContext(ctx); err != nil {
		a.writeCommandError(opts.output, err)
		return 1
	}
	return 0
}
