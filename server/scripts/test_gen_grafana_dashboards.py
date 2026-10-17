#!/usr/bin/env python3
################################################################################
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################
"""Regression tests for the Grafana dashboard asset generator."""

import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_grafana_dashboards import gauge_panel, generate_dashboards, layout_panels, specs

DASHBOARD_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "grafana"


def panel(width, height):
    return {"gridPos": {"w": width, "h": height, "x": 99, "y": 99}}


def shipped_panel_expr(uid, title):
    """Expression a shipped dashboard asset renders in the panel named ``title``."""
    with open(DASHBOARD_DIR / f"{uid}.json", encoding="utf-8") as handle:
        dashboard = json.load(handle)
    for item in dashboard["panels"]:
        if item["title"] == title:
            return item["targets"][0]["expr"]
    raise AssertionError(f"panel {title!r} missing from {uid}")


class LayoutPanelsTest(unittest.TestCase):

    def test_packs_panels_by_width_and_advances_by_tallest_panel(self):
        panels = [panel(12, 8), panel(12, 8), panel(6, 6), panel(6, 8), panel(12, 6)]

        laid_out = layout_panels(panels)

        self.assertEqual(
            [(item["gridPos"]["x"], item["gridPos"]["y"]) for item in laid_out],
            [(0, 0), (12, 0), (0, 8), (6, 8), (12, 8)],
        )
        self.assertEqual(panels[0]["gridPos"]["x"], 99)
        self.assertEqual(panels[0]["gridPos"]["y"], 99)

    def test_starts_a_new_row_when_the_next_panel_does_not_fit(self):
        laid_out = layout_panels([panel(8, 6), panel(8, 10), panel(12, 4)])

        self.assertEqual(
            [(item["gridPos"]["x"], item["gridPos"]["y"]) for item in laid_out],
            [(0, 0), (8, 0), (0, 10)],
        )

    def test_rejects_invalid_dimensions(self):
        with self.assertRaises(ValueError):
            layout_panels([panel(25, 8)])
        with self.assertRaises(ValueError):
            layout_panels([panel(12, 0)])


class OverviewPanelsTest(unittest.TestCase):

    def specs_by_uid(self):
        return {spec[0]: spec for spec in specs}

    def test_broker_count_counts_brokers_not_series(self):
        overview = self.specs_by_uid()["rocketmq-overview"]
        panels = {item["title"]: item for item in overview[3]}
        expr = panels["Broker Count"]["targets"][0]["expr"]

        # rocketmq_messages_in_total carries both broker and topic labels - the same dashboard
        # derives its $broker and $topic template variables from them - so a bare count() returns
        # one series per broker/topic pair and reads hundreds on a two-broker cluster. The
        # neighbouring "Total Topics" panel already counts with count(count by (topic) (...)).
        self.assertEqual(
            'count(count by (broker) (rocketmq_messages_in_total{cluster="$cluster"}))',
            expr,
        )

    def test_shipped_overview_dashboard_matches_the_generator(self):
        overview = self.specs_by_uid()["rocketmq-overview"]
        panels = {item["title"]: item for item in overview[3]}

        self.assertEqual(
            panels["Broker Count"]["targets"][0]["expr"],
            shipped_panel_expr("rocketmq-overview", "Broker Count"),
        )


class ShippedDashboardPanelsTest(unittest.TestCase):

    def test_dlq_resend_count_panel_counts_over_the_window(self):
        # The panel title and its "short" unit promise a count over the 1m window, but rate()
        # renders resends per second - 60x below the number a reader takes from the title. The
        # sibling "Reject Count (1m)" panel in the same bundle already uses increase(...[1m]).
        self.assertEqual(
            'increase(rocketmq_dlq_resend_count{cluster="$cluster"}[1m])',
            shipped_panel_expr("rocketmq-dlq", "DLQ Resend Count (1m)"),
        )

    def test_shipped_dlq_dashboard_matches_the_generator(self):
        dlq = {spec[0]: spec for spec in specs}["rocketmq-dlq"]
        panels = {item["title"]: item for item in dlq[3]}

        self.assertEqual(
            panels["DLQ Resend Count (1m)"]["targets"][0]["expr"],
            shipped_panel_expr("rocketmq-dlq", "DLQ Resend Count (1m)"),
        )


class ConsumerDashboardTest(unittest.TestCase):

    def test_consumer_tps_uses_native_consumer_group_label(self):
        consumer = next(spec for spec in specs if spec[0] == "rocketmq-consumer")
        target = next(item for item in consumer[3] if item["title"] == "Messages Out TPS by Group")["targets"][0]

        self.assertEqual(
            'sum by (consumer_group) (rate(rocketmq_messages_out_total{cluster="$cluster",consumer_group="$group"}[1m]))',
            target["expr"],
        )
        self.assertEqual("{{consumer_group}}", target["legendFormat"])

    def test_consumer_group_selector_and_shipped_asset_are_consistent(self):
        with tempfile.TemporaryDirectory() as generated_dir:
            generate_dashboards(generated_dir)
            generated = json.loads((Path(generated_dir) / "rocketmq-consumer.json").read_text(encoding="utf-8"))
            shipped = json.loads((DASHBOARD_DIR / "rocketmq-consumer.json").read_text(encoding="utf-8"))

            variables = {variable["name"]: variable for variable in generated["templating"]["list"]}
            self.assertIn("group", variables)
            self.assertEqual(
                'label_values(rocketmq_messages_out_total{cluster="$cluster"}, consumer_group)',
                variables["group"]["query"],
            )
            self.assertEqual({"type": "prometheus", "uid": "${DS_PROMETHEUS}"}, variables["group"]["datasource"])
            self.assertEqual(2, variables["group"]["refresh"])
            panel_variables = {
                name
                for panel in generated["panels"]
                for target in panel["targets"]
                for name in re.findall(r"\$(\w+)", target["expr"])
            }
            self.assertFalse(panel_variables - variables.keys())
            self.assertEqual(generated, shipped)

            for uid, _, _, _ in specs:
                if uid != "rocketmq-consumer":
                    other = json.loads((Path(generated_dir) / f"{uid}.json").read_text(encoding="utf-8"))
                    self.assertNotIn("group", {variable["name"] for variable in other["templating"]["list"]})


class GaugePanelTest(unittest.TestCase):

    def test_places_minimum_in_field_defaults(self):
        gauge = gauge_panel(1, "Disk", "metric", 12, 0)

        defaults = gauge["fieldConfig"]["defaults"]
        self.assertEqual(defaults["min"], 0)
        self.assertNotIn("min", defaults["custom"])


if __name__ == "__main__":
    unittest.main()
