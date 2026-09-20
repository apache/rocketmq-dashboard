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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drift alarm for the cross-language event contract.
 *
 * <p>{@code src/test/resources/ai/ai-event-contract.json} is the single source of truth shared with
 * the web client, whose contract test reads the same file. This test fails whenever the Java wire
 * types stop matching it: a subtype added, removed or renamed; a discriminator that no longer
 * round-trips; an optional field that starts appearing while null; a field that stops appearing;
 * a record component nobody declared in the fixture. Fix the code and the fixture together, and
 * update the web client in the same change.
 *
 * <p>Every fixture entry carries two examples and one declaration:
 * <ul>
 *   <li>{@code example} — the richest legal wire form. It populates every field except the ones that
 *       cannot legitimately appear together with it (a successful {@code tool_done} has no
 *       {@code error}).</li>
 *   <li>{@code absent} — the OPTIONAL fields, the ones {@code @JsonInclude(NON_NULL)} drops whenever
 *       the server has nothing to say. This is the half of the contract a single example cannot
 *       show: it pins that a missing key stays missing instead of travelling as an explicit null.</li>
 *   <li>{@code exampleMinimal} — the leanest legal wire form, which therefore carries exactly the
 *       required fields. For a type with no optional field it doubles as a second example widening
 *       value coverage, which is how {@code notice} reaches {@code level=error} and {@code run_status}
 *       reaches a completed run with no reason.</li>
 * </ul>
 *
 * <p>{@code absent} states what the CLIENT may miss, which is deliberately narrower than what a
 * record component can hold. {@code AiEventJsonSerializationTest} also treats {@code input},
 * {@code source}, {@code output}, {@code outputBytes} and {@code durationMs} as nullable, because
 * {@code NON_NULL} is a property of the type rather than of one producer; this fixture does not
 * follow, because {@code AgentEventProjector} guarantees a non-null tool {@code input} and thinking
 * {@code source} and because {@code web/src/api/aiEvents.ts} types the tool output fields as
 * required. Widening {@code absent} here therefore means widening that mirror, and the reducers that
 * copy those fields onto a render block, in the same change.
 *
 * <p>The two closure rules below are what make an entry self-checking, and both suites assert them:
 * {@code components(subtype) == keys(example) U absent} and
 * {@code keys(exampleMinimal) == components(subtype) - absent}. The first one is the reason a new
 * record component cannot slip into the wire format unnoticed: it appears in neither example nor
 * {@code absent}, so the sets stop matching.
 *
 * <p>Every case runs against both mappers on the classpath, see {@link AiEventCodecs}.
 */
class AiEventContractTest {

    private static final String CONTRACT_RESOURCE = "/ai/ai-event-contract.json";
    private static final int EXPECTED_CONTRACT_VERSION = 2;
    private static final String DISCRIMINATOR = "type";
    private static final List<String> SECTIONS = List.of("live", "timeline");

    private static final JsonNode CONTRACT = readContract();

    static Stream<Arguments> contractCases() {
        List<Arguments> cases = new ArrayList<>();
        for (AiEventCodecs.Codec codec : AiEventCodecs.all()) {
            for (ContractCase contractCase : allContractCases()) {
                cases.add(Arguments.of(codec.label() + " " + contractCase.label(), codec, contractCase));
            }
        }
        return cases.stream();
    }

    @Test
    void contractVersionIsUnderstoodTest() {
        assertThat(CONTRACT.path("version").asInt())
                .as("contract fixture version; a bump means this test must be revisited")
                .isEqualTo(EXPECTED_CONTRACT_VERSION);
    }

    @Test
    void liveEventTypesMatchContractTest() {
        assertTypesMatchContract("live", LiveEvent.class);
    }

    @Test
    void timelineEventTypesMatchContractTest() {
        assertTypesMatchContract("timeline", TimelineEvent.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCases")
    void fixtureExampleDeserialisesIntoItsDeclaredSubtypeTest(String label, AiEventCodecs.Codec codec,
                                                             ContractCase contractCase) {
        Object event = codec.read(contractCase.exampleJson(), contractCase.baseType());

        assertThat(event)
                .as("%s: the example must deserialise into the subtype carrying that @JsonTypeName",
                        label)
                .isInstanceOf(contractCase.expectedSubtype());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCases")
    void fixtureExampleReserialisesToTheSameJsonTreeTest(String label, AiEventCodecs.Codec codec,
                                                        ContractCase contractCase) {
        Object event = codec.read(contractCase.exampleJson(), contractCase.baseType());

        JsonNode written = AiEventCodecs.TREES.readTree(codec.write(event));
        JsonNode expected = AiEventCodecs.TREES.readTree(contractCase.exampleJson());

        assertThat(written)
                .as("%s: re-serialising must reproduce the fixture field for field, and null "
                        + "optional fields must stay absent under @JsonInclude(NON_NULL)", label)
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCases")
    void fixtureMinimalExampleDeserialisesIntoItsDeclaredSubtypeTest(String label, AiEventCodecs.Codec codec,
                                                                    ContractCase contractCase) {
        Object event = codec.read(contractCase.minimalJson(), contractCase.baseType());

        assertThat(event)
                .as("%s: the minimal example must deserialise into the same subtype as the full one; "
                        + "dropping an optional field must not make the frame unreadable", label)
                .isInstanceOf(contractCase.expectedSubtype());
    }

    /**
     * The {@code @JsonInclude(NON_NULL)} half of the contract, pinned by the SHARED fixture rather
     * than by a Java-only case list, so the web client asserts the very same absence.
     *
     * <p>Two failures are worth telling apart here. A key written as an explicit null means
     * {@code NON_NULL} was lost, and the client sees {@code "error": null} where its type says the
     * key is simply not there. A required key that vanished means {@code NON_NULL} was widened into
     * {@code NON_DEFAULT}, which silently drops a legitimate {@code false} or {@code 0} — the UI
     * could no longer tell a successful call from one whose result never arrived.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCases")
    void fixtureMinimalExampleReserialisesWithoutTheAbsentFieldsTest(String label, AiEventCodecs.Codec codec,
                                                                    ContractCase contractCase) {
        Object event = codec.read(contractCase.minimalJson(), contractCase.baseType());

        JsonNode written = AiEventCodecs.TREES.readTree(codec.write(event));

        assertThat(written)
                .as("%s: the minimal form must round-trip field for field", label)
                .isEqualTo(AiEventCodecs.TREES.readTree(contractCase.minimalJson()));
        assertThat(written.path(DISCRIMINATOR).asString(null))
                .as("%s: the minimal form must keep its discriminator", label)
                .isEqualTo(contractCase.type());
        for (String name : contractCase.absent()) {
            assertThat(written.has(name))
                    .as("%s: an absent optional field must not be written at all, not even as null: %s",
                            label, written)
                    .isFalse();
        }
        for (String name : requiredFields(contractCase)) {
            assertThat(written.has(name))
                    .as("%s: the required field %s must survive the minimal form: %s", label, name, written)
                    .isTrue();
        }
    }

    /**
     * Absence must mean {@code null} on the way back in, not an invented default and not a failure:
     * a client that omits {@code hint} and a server that reads it as null are the two halves of the
     * same promise. The full example is checked in the same breath, so a field can only be declared
     * optional if some example still shows it populated.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contractCases")
    void fixtureAbsentFieldsReadBackAsNullTest(String label, AiEventCodecs.Codec codec,
                                              ContractCase contractCase) {
        Object minimal = codec.read(contractCase.minimalJson(), contractCase.baseType());
        Object full = codec.read(contractCase.exampleJson(), contractCase.baseType());
        Set<String> exampleFields = payloadFields(contractCase.exampleJson());

        for (String name : contractCase.absent()) {
            RecordComponent component = component(contractCase.expectedSubtype(), name);

            assertThat(valueOf(component, minimal))
                    .as("%s: an absent %s must deserialise as null", label, name)
                    .isNull();
            if (exampleFields.contains(name)) {
                assertThat(valueOf(component, full))
                        .as("%s: the full example must still populate the optional %s, otherwise the "
                                + "fixture stops showing what the field looks like when present", label, name)
                        .isNotNull();
            }
        }
    }

    /**
     * Closure of the fixture against the wire types, in both directions.
     *
     * <p>Without it a new record component would be invisible here: {@code NON_NULL} keeps a null
     * component out of the JSON, so the re-serialisation of an example that never sets it still
     * matches the fixture. Enumerating the components by reflection is what turns "someone added a
     * field to {@code ToolDone}" into a red build instead of into a client that never learns about it.
     */
    @Test
    void fixtureAccountsForEveryFieldOfEverySubtypeTest() {
        for (ContractCase contractCase : allContractCases()) {
            Set<String> components = componentNames(contractCase.expectedSubtype());
            Set<String> absent = new LinkedHashSet<>(contractCase.absent());
            Set<String> exampleFields = payloadFields(contractCase.exampleJson());
            Set<String> minimalFields = payloadFields(contractCase.minimalJson());

            assertThat(absent)
                    .as("%s: every entry of absent[] must name a component of %s", contractCase.label(),
                            contractCase.expectedSubtype().getSimpleName())
                    .isSubsetOf(components);
            assertThat(exampleFields)
                    .as("%s: the example must not carry a field %s does not have", contractCase.label(),
                            contractCase.expectedSubtype().getSimpleName())
                    .isSubsetOf(components);
            assertThat(minimalFields)
                    .as("%s: the minimal example must not carry a field %s does not have", contractCase.label(),
                            contractCase.expectedSubtype().getSimpleName())
                    .isSubsetOf(components);

            Set<String> declared = new LinkedHashSet<>(exampleFields);
            declared.addAll(absent);
            assertThat(declared)
                    .as("%s: example plus absent[] must account for every component of %s; a field in "
                                    + "neither is one the web client will never be told about",
                            contractCase.label(), contractCase.expectedSubtype().getSimpleName())
                    .isEqualTo(components);
            assertThat(minimalFields)
                    .as("%s: exampleMinimal must carry exactly the required fields, so that a required "
                            + "field cannot be dropped from the minimal form unnoticed", contractCase.label())
                    .isEqualTo(difference(components, absent));

            for (String name : absent) {
                assertThat(component(contractCase.expectedSubtype(), name).getType().isPrimitive())
                        .as("%s: %s is a primitive component and is therefore always written; it can "
                                + "never be absent", contractCase.label(), name)
                        .isFalse();
            }
        }
    }

    /**
     * All three notice levels appear in the SHARED fixture, {@code error} included.
     *
     * <p>{@code error} is a notice level rather than an {@link LiveEvent.Error} because the run
     * survives it: {@code ClaudeCodeStreamParser} emits one for a {@code result} frame carrying
     * {@code errors} and for a non-null {@code api_error_status}. The single {@code notice} example
     * per section cannot show three levels, so {@code exampleMinimal} carries the third one — that is
     * what the second example slot is for on a type with no optional field.
     */
    @Test
    void fixtureNoticeLevelsCoverTheWholeVocabularyTest() {
        assertThat(List.of(AgentEventProjector.LEVEL_INFO, AgentEventProjector.LEVEL_WARN,
                AgentEventProjector.LEVEL_ERROR))
                .as("the projector's notice levels are the closed vocabulary the client mirrors")
                .containsExactly("info", "warn", "error");

        Set<String> levels = new LinkedHashSet<>();
        for (String section : SECTIONS) {
            for (JsonNode entry : CONTRACT.path(section)) {
                if (!"notice".equals(entry.path("type").asString(null))) {
                    continue;
                }
                levels.add(entry.path("example").path("level").asString(null));
                levels.add(entry.path("exampleMinimal").path("level").asString(null));
            }
        }

        assertThat(levels)
                .as("%s must show every notice level, including the error level a single example "
                        + "per section cannot reach", CONTRACT_RESOURCE)
                .containsExactlyInAnyOrder(AgentEventProjector.LEVEL_INFO, AgentEventProjector.LEVEL_WARN,
                        AgentEventProjector.LEVEL_ERROR);
    }

    private static void assertTypesMatchContract(String section, Class<?> baseType) {
        List<String> fixtureTypes = fixtureTypes(section);
        Set<String> declared = declaredTypeNames(baseType);

        assertThat(fixtureTypes)
                .as("the %s[] section of %s must not list a type twice", section, CONTRACT_RESOURCE)
                .doesNotHaveDuplicates();
        assertThat(declared)
                .as("@JsonTypeName values on the permitted subclasses of %s must equal the %s[] "
                                + "types of %s", baseType.getSimpleName(), section, CONTRACT_RESOURCE)
                .isEqualTo(new LinkedHashSet<>(fixtureTypes));
        assertThat(registeredSubTypeNames(baseType))
                .as("@JsonSubTypes on %s must list exactly the sealed subtypes; Jackson 2 cannot "
                        + "discover them from the sealed hierarchy", baseType.getSimpleName())
                .isEqualTo(declared);

        JsonTypeInfo typeInfo = baseType.getAnnotation(JsonTypeInfo.class);
        assertThat(typeInfo)
                .as("%s must stay a NAME-based polymorphic hierarchy on the \"type\" property",
                        baseType.getSimpleName())
                .isNotNull();
        assertThat(typeInfo.use()).isEqualTo(JsonTypeInfo.Id.NAME);
        assertThat(typeInfo.property()).isEqualTo(DISCRIMINATOR);
    }

    private static List<String> fixtureTypes(String section) {
        JsonNode entries = CONTRACT.path(section);
        assertThat(entries.isArray())
                .as("%s must be an array of {type, example, exampleMinimal, absent} entries", section)
                .isTrue();
        List<String> types = new ArrayList<>();
        for (JsonNode entry : entries) {
            String type = entry.path("type").asString(null);
            assertThat(type).as("every %s[] entry must declare a type", section).isNotBlank();
            assertThat(exampleOf(section, entry, type, "example").path(DISCRIMINATOR).asString(null))
                    .as("the example of %s entry %s must carry the same type", section, type)
                    .isEqualTo(type);
            assertThat(exampleOf(section, entry, type, "exampleMinimal").path(DISCRIMINATOR).asString(null))
                    .as("the exampleMinimal of %s entry %s must carry the same type", section, type)
                    .isEqualTo(type);
            absentOf(section, entry, type);
            types.add(type);
        }
        return types;
    }

    private static JsonNode exampleOf(String section, JsonNode entry, String type, String key) {
        JsonNode example = entry.path(key);
        assertThat(example.isObject())
                .as("the %s entry %s must have an object %s", section, type, key)
                .isTrue();
        return example;
    }

    private static List<String> absentOf(String section, JsonNode entry, String type) {
        JsonNode absent = entry.path("absent");
        assertThat(absent.isArray())
                .as("the %s entry %s must declare its optional fields as an absent[] array, empty when "
                        + "the type has none", section, type)
                .isTrue();
        List<String> names = new ArrayList<>();
        for (JsonNode name : absent) {
            assertThat(name.isString())
                    .as("every absent[] entry of %s/%s must be a field name", section, type)
                    .isTrue();
            assertThat(name.asString()).isNotBlank();
            names.add(name.asString());
        }
        assertThat(names)
                .as("the absent[] of %s entry %s must not repeat a field", section, type)
                .doesNotHaveDuplicates();
        return names;
    }

    private static List<ContractCase> allContractCases() {
        List<ContractCase> cases = new ArrayList<>();
        cases.addAll(contractCases("live", LiveEvent.class));
        cases.addAll(contractCases("timeline", TimelineEvent.class));
        return cases;
    }

    private static List<ContractCase> contractCases(String section, Class<?> baseType) {
        List<ContractCase> cases = new ArrayList<>();
        for (JsonNode entry : CONTRACT.path(section)) {
            String type = entry.path("type").asString(null);
            cases.add(new ContractCase(section, baseType, type,
                    exampleOf(section, entry, type, "example").toString(),
                    exampleOf(section, entry, type, "exampleMinimal").toString(),
                    absentOf(section, entry, type),
                    subtypeForWireName(baseType, type)));
        }
        return cases;
    }

    private static Set<String> requiredFields(ContractCase contractCase) {
        return difference(componentNames(contractCase.expectedSubtype()), contractCase.absent());
    }

    /** The field names of a written example, minus the discriminator that is not a component. */
    private static Set<String> payloadFields(String json) {
        JsonNode node = AiEventCodecs.TREES.readTree(json);
        assertThat(node.isObject()).as("%s must be a JSON object", json).isTrue();
        Set<String> names = new LinkedHashSet<>(node.propertyNames());
        assertThat(names.remove(DISCRIMINATOR))
                .as("%s must carry the discriminator", json)
                .isTrue();
        return names;
    }

    private static Set<String> componentNames(Class<?> subtype) {
        Set<String> names = new LinkedHashSet<>();
        for (RecordComponent component : componentsOf(subtype)) {
            names.add(component.getName());
        }
        return names;
    }

    private static RecordComponent[] componentsOf(Class<?> subtype) {
        RecordComponent[] components = subtype.getRecordComponents();
        assertThat(components)
                .as("%s must stay a record so the contract test can enumerate its fields by reflection",
                        subtype.getSimpleName())
                .isNotNull();
        return components;
    }

    private static RecordComponent component(Class<?> subtype, String name) {
        for (RecordComponent component : componentsOf(subtype)) {
            if (component.getName().equals(name)) {
                return component;
            }
        }
        throw new AssertionError(subtype.getSimpleName() + " has no record component " + name);
    }

    private static Object valueOf(RecordComponent component, Object instance) {
        try {
            return component.getAccessor().invoke(instance);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot read " + component.getName() + " of " + instance, e);
        }
    }

    private static Set<String> difference(Set<String> all, Collection<String> minus) {
        Set<String> rest = new LinkedHashSet<>(all);
        rest.removeAll(minus);
        return rest;
    }

    private static Class<?> subtypeForWireName(Class<?> baseType, String wireName) {
        for (Class<?> subtype : baseType.getPermittedSubclasses()) {
            JsonTypeName typeName = subtype.getAnnotation(JsonTypeName.class);
            if (typeName != null && typeName.value().equals(wireName)) {
                return subtype;
            }
        }
        throw new AssertionError("no subtype of " + baseType.getSimpleName()
                + " is annotated @JsonTypeName(\"" + wireName + "\")");
    }

    private static Set<String> declaredTypeNames(Class<?> baseType) {
        Class<?>[] subtypes = baseType.getPermittedSubclasses();
        assertThat(subtypes)
                .as("%s must stay sealed so the contract test can enumerate it by reflection",
                        baseType.getSimpleName())
                .isNotNull();
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> subtype : subtypes) {
            JsonTypeName typeName = subtype.getAnnotation(JsonTypeName.class);
            assertThat(typeName)
                    .as("%s must declare its wire name with @JsonTypeName", subtype.getSimpleName())
                    .isNotNull();
            assertThat(typeName.value()).isNotBlank();
            names.add(typeName.value());
        }
        assertThat(names)
                .as("@JsonTypeName values on %s must be unique", baseType.getSimpleName())
                .hasSize(subtypes.length);
        return names;
    }

    private static Set<String> registeredSubTypeNames(Class<?> baseType) {
        JsonSubTypes subTypes = baseType.getAnnotation(JsonSubTypes.class);
        assertThat(subTypes)
                .as("%s must register its subtypes with @JsonSubTypes", baseType.getSimpleName())
                .isNotNull();
        Set<String> names = new LinkedHashSet<>();
        for (JsonSubTypes.Type type : subTypes.value()) {
            names.add(type.name());
        }
        assertThat(names)
                .as("@JsonSubTypes on %s must not repeat a name", baseType.getSimpleName())
                .hasSize(subTypes.value().length);
        return names;
    }

    private static JsonNode readContract() {
        try (InputStream in = AiEventContractTest.class.getResourceAsStream(CONTRACT_RESOURCE)) {
            if (in == null) {
                throw new AssertionError("missing contract fixture on the classpath: " + CONTRACT_RESOURCE);
            }
            return AiEventCodecs.TREES.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + CONTRACT_RESOURCE, e);
        }
    }

    /**
     * One fixture entry: a wire name, its two examples and its declared optional fields, resolved
     * against a base type.
     */
    private record ContractCase(String section, Class<?> baseType, String type, String exampleJson,
                                String minimalJson, List<String> absent, Class<?> expectedSubtype) {

        String label() {
            return section + "/" + type;
        }
    }
}
