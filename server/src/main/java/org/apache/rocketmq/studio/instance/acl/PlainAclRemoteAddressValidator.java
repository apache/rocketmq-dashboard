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
package org.apache.rocketmq.studio.instance.acl;

import org.apache.commons.validator.routines.InetAddressValidator;

import java.util.regex.Pattern;

/**
 * Validates the address-expression grammar used by RocketMQ plain ACL.
 *
 * <p>This is deliberately separate from {@link IpRangeMatcher}: plain ACL does not use CIDR.
 * Besides exact IPv4/IPv6 addresses it accepts the wildcard, range, comma-list and final-segment
 * brace forms understood by RocketMQ's legacy {@code RemoteAddressStrategyFactory}.</p>
 */
final class PlainAclRemoteAddressValidator {

    private static final InetAddressValidator INET_ADDRESS_VALIDATOR = InetAddressValidator.getInstance();
    private static final Pattern DECIMAL = Pattern.compile("\\d{1,3}");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{1,4}");

    private PlainAclRemoteAddressValidator() {
    }

    static boolean isValid(String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        if (!expression.equals(expression.trim()) || containsWhitespace(expression)) {
            return false;
        }
        if (isAllAddresses(expression)) {
            return true;
        }
        if (expression.indexOf('/') >= 0) {
            return false;
        }
        if (expression.indexOf('{') >= 0 || expression.indexOf('}') >= 0) {
            return isValidFinalSegmentSet(expression);
        }
        if (expression.indexOf(',') >= 0) {
            return isValidAddressList(expression);
        }
        if (isExactAddress(expression)) {
            return true;
        }
        if (expression.indexOf(':') >= 0) {
            return isValidIpv6Range(expression);
        }
        return isValidIpv4Range(expression);
    }

    private static boolean isAllAddresses(String expression) {
        return "*".equals(expression)
                || "*.*.*.*".equals(expression)
                || "*:*:*:*:*:*:*:*".equals(expression);
    }

    private static boolean containsWhitespace(String expression) {
        for (int i = 0; i < expression.length(); i++) {
            if (Character.isWhitespace(expression.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExactAddress(String address) {
        return INET_ADDRESS_VALIDATOR.isValidInet4Address(address)
                || INET_ADDRESS_VALIDATOR.isValidInet6Address(address);
    }

    private static boolean isValidAddressList(String expression) {
        String[] addresses = expression.split(",", -1);
        if (addresses.length < 2) {
            return false;
        }
        for (String address : addresses) {
            if (!isExactAddress(address)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidFinalSegmentSet(String expression) {
        int openingBrace = expression.indexOf('{');
        if (openingBrace <= 0
                || openingBrace != expression.lastIndexOf('{')
                || expression.indexOf('}') != expression.length() - 1
                || expression.indexOf('}') != expression.lastIndexOf('}')) {
            return false;
        }
        char separator = expression.indexOf(':') >= 0 ? ':' : '.';
        if (expression.charAt(openingBrace - 1) != separator) {
            return false;
        }
        String prefix = expression.substring(0, openingBrace);
        String members = expression.substring(openingBrace + 1, expression.length() - 1);
        String[] values = members.split(",", -1);
        if (values.length == 0) {
            return false;
        }
        for (String value : values) {
            if (value.isEmpty() || !isExactAddress(prefix + value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidIpv4Range(String expression) {
        String[] segments = expression.split("\\.", -1);
        if (segments.length != 4 || !isDecimalOctet(segments[0])) {
            return false;
        }
        int variableIndex = -1;
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            if (variableIndex < 0 && isDecimalOctet(segment)) {
                continue;
            }
            if (variableIndex < 0 && ("*".equals(segment) || isValidRange(segment, 10, 255))) {
                variableIndex = i;
                continue;
            }
            if (variableIndex >= 0 && "*".equals(segment)) {
                continue;
            }
            return false;
        }
        return variableIndex >= 0;
    }

    private static boolean isValidIpv6Range(String expression) {
        if (expression.indexOf('.') >= 0 || expression.indexOf("::") != expression.lastIndexOf("::")) {
            return false;
        }
        String[] segments = expression.split(":", -1);
        int nonEmptySegments = 0;
        int prefixGroups = 0;
        int firstVariable = -1;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                continue;
            }
            nonEmptySegments++;
            if (HEX.matcher(segment).matches()) {
                if (firstVariable >= 0) {
                    return false;
                }
                continue;
            }
            if (firstVariable < 0 && ("*".equals(segment) || isValidRange(segment, 16, 0xffff))) {
                prefixGroups = nonEmptySegments - 1;
                firstVariable = i;
                continue;
            }
            if (firstVariable >= 0 && "*".equals(segment) && i == segments.length - 1) {
                continue;
            }
            return false;
        }
        // split(":", -1) keeps the empty tokens of a leading "::", so the variable's array index
        // alone cannot prove a concrete prefix exists. Expressions like "::*" or "::1-20" anchor
        // the range on the first group and cannot be used by the plain ACL address parser.
        if (prefixGroups < 1 || nonEmptySegments > 8) {
            return false;
        }
        String variable = segments[firstVariable];
        if ("*".equals(variable)) {
            return firstVariable == segments.length - 1;
        }
        return firstVariable == segments.length - 1
                || firstVariable == segments.length - 2 && "*".equals(segments[segments.length - 1]);
    }

    private static boolean isDecimalOctet(String value) {
        if (!DECIMAL.matcher(value).matches()) {
            return false;
        }
        return Integer.parseInt(value) <= 255;
    }

    private static boolean isValidRange(String value, int radix, int maximum) {
        int separator = value.indexOf('-');
        if (separator <= 0 || separator != value.lastIndexOf('-') || separator == value.length() - 1) {
            return false;
        }
        String startValue = value.substring(0, separator);
        String endValue = value.substring(separator + 1);
        Pattern componentPattern = radix == 10 ? DECIMAL : HEX;
        if (!componentPattern.matcher(startValue).matches() || !componentPattern.matcher(endValue).matches()) {
            return false;
        }
        try {
            int start = Integer.parseInt(startValue, radix);
            int end = Integer.parseInt(endValue, radix);
            return start <= end && end <= maximum;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
