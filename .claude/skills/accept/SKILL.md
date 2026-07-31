---
name: accept
model: claude-sonnet-4-6
allowed-tools: Read, Write, Edit, Bash
description: >-
  Write a failing acceptance test for the NEXT spec rule (Step 2 of the
  development process). Use after a spec in docs/specs/ is finalised and before
  any production code is written for that rule. One rule at a time — the test
  must fail for the right reason.
argument-hint: "<rule name> @docs/specs/<feature>.md"
---

Write a failing acceptance test for: $ARGUMENTS

Read CLAUDE.md for project conventions before writing anything.
Re-read the spec file to understand the full rule, its examples, and its counter-examples.

## Structure

One test class per story/feature, named `<Feature>AcceptanceIT`.
One `@Nested` inner class per rule — name it after the rule.
One `@Test` per example AND per counter-example from the spec — **unless the rule is data-driven**.

When a rule's examples form a table — several rows of the same shape (a rate
table, a set of boundary values, an input → output mapping) — render it as a
single JUnit 6 `@ParameterizedTest` with one case per row, **not** one `@Test`
per row. The spec's table becomes the `@CsvSource`; counter-example rows are
just more rows. See "Data-driven rules" below.

Use `@DisplayName` with the spec's exact business language:
- Outer class: the feature name
- `@Nested` class: the rule
- Method: the "The one where…" text from the spec (counter-examples too)

`@Nested` classes inherit the outer class's Spring context (`@NestedTestConfiguration`
defaults to `INHERIT`), so annotate and `@Autowired` the test client once on the outer class.
See `.claude/skills/tdd/SKILL.md` → "Acceptance test" for the full worked template.

## How to test

Test through the REST API. This service runs on **Spring Boot 4** (JUnit 6), so use
`@SpringBootTest` + `@AutoConfigureMockMvc` and inject the AssertJ-based `MockMvcTester`
(`org.springframework.test.web.servlet.assertj.MockMvcTester`) — NOT the old
`mockMvc.perform(...).andExpect(...)` chain, and NOT `@MockBean` (removed in Boot 4).

`@AutoConfigureMockMvc` lives in `org.springframework.boot.webmvc.test.autoconfigure`
and needs the `spring-boot-starter-webmvc-test` test dependency.

Send real HTTP requests. Assert real HTTP responses.
NEVER call services or domain objects directly — this is an acceptance test, not a unit test.

Skeleton (replace the placeholder `<Feature>` / `<rule>` / endpoint with the spec's):

```java
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("<feature name>")
class <Feature>AcceptanceIT {

    @Autowired
    private MockMvcTester mvc;

    @Nested
    @DisplayName("<the rule, in the spec's words>")
    class <Rule> {
        @Test
        @DisplayName("The one where <the example>")
        void <example>() {
            MvcTestResult result = mvc.post().uri("/api/<resource>")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            { ...request fields from the example... }
                            """)
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson()
                    .extractingPath("$.<field>")
                    .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo("<expected>");
        }
    }
}
```

Assert exact values from the spec examples. For money, compare with `compareTo`, never
`equals`: `extractingPath("$.amount").convertTo(InstanceOfAssertFactories.BIG_DECIMAL).isEqualByComparingTo("1.60")`.
Do NOT use `.asNumber().isEqualTo(new BigDecimal(...))` — it compares a parsed `Double`
to a `BigDecimal` and fails.

## Data-driven rules → one parameterized test

If the rule's examples are a table of same-shaped rows, use a single
`@ParameterizedTest` inside the `@Nested` rule class. Keep the Example-Mapping
voice by templating the row into the `name`, so every case still reads as
"The one where…" in the report. (`junit-jupiter-params` is already on the
classpath via `spring-boot-starter-test`.)

```java
@Nested
@DisplayName("The line total is the unit price times the quantity")
class UnitPriceTimesQuantity {

    @ParameterizedTest(name = "The one where {0} items at £{1} total £{2}")
    @CsvSource({
            "1, 2.50,  2.50",
            "3, 2.50,  7.50",
            "4, 10.00, 40.00",
    })
    void lineTotalIsUnitPriceTimesQuantity(String quantity, String unitPrice, String expectedTotal) {
        MvcTestResult result = mvc.post().uri("/api/order-lines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "sku": "A1", "quantity": %s, "unitPrice": %s }
                        """.formatted(quantity, unitPrice))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson()
                .extractingPath("$.total")
                .convertTo(InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo(expectedTotal);
    }
}
```

Imports: `org.junit.jupiter.params.ParameterizedTest`,
`org.junit.jupiter.params.provider.CsvSource`. Keep money expectations as
`String` and assert with `isEqualByComparingTo(...)`. Include the rows on each
side of a boundary (the last row before a classification flips and the first
after), exactly the boundaries you'd otherwise write as separate `@Test`s. For more than ~6 rows,
or when values come from existing constants/enums, switch the source to
`@MethodSource` / `@FieldSource` so the table stays readable.

## What NOT to do

Do NOT write production code. The test MUST FAIL.
A passing test means you tested nothing.
Do NOT write tests for all rules at once.
One rule only — the one specified in the arguments.
Do NOT invent examples beyond what the spec provides.
The spec is the contract.
Do NOT use mocks in acceptance tests.
Wire the full stack: controller → service → domain → persistence.

## When you're done

Run the test. Confirm it fails for the RIGHT reason:
- Missing endpoint → 404 or compilation error (good)
- Wrong value → not yet, the endpoint shouldn't exist
- Test passes → something is wrong, investigate

Report: which rule you tested, how many examples, and the failure reason.

STOP. Do not proceed to implementation.
