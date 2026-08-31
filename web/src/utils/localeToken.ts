/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

/**
 * Locale-stable lowercasing for comparison against fixed tokens.
 *
 * Plain toLowerCase() applies the host browser's locale rules, so a fixed
 * token such as "CRITICAL" becomes "cRıTıCAL" under the Turkish locale and
 * silently fails an equality check against "critical". The 'en' locale maps
 * ASCII letters identically to the root locale; 'root' itself is rejected by
 * some V8/ICU builds with "Incorrect locale information provided".
 */
export const toToken = (value: string | null | undefined): string =>
  (value ?? '').toLocaleLowerCase('en');
