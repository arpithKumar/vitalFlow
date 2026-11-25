"""Tests for check_release_mapping.py. Run: python3 -m unittest discover -s scripts"""

import unittest

import check_release_mapping as guard

MODEL = "com.vitalflow.app.data.entity.Vital"
PREFIXES = ("com.vitalflow.app.data.entity.",)


class DeclaredClassesTest(unittest.TestCase):
    def test_nested_classes_use_dollar_names(self):
        source = """
        package com.example;
        /** class Fake in a comment */
        public class Outer {
            String s = "class Fake in a string";
            public static class Inner { private static class Deep { } }
            enum Kind { A }
        }
        class Other { }
        """
        self.assertEqual(
            ["com.example.Outer", "com.example.Outer$Inner", "com.example.Outer$Inner$Deep",
             "com.example.Outer$Kind", "com.example.Other"],
            guard.declared_classes(source))

    def test_class_literals_and_anonymous_classes_are_not_declarations(self):
        source = """
        package p;
        class A { void f() { g(A.class); new Runnable() { public void run() { } }; } }
        """
        self.assertEqual(["p.A"], guard.declared_classes(source))


class CheckTest(unittest.TestCase):
    def check(self, mapping):
        return guard.check(mapping, {MODEL}, PREFIXES)

    def test_kept_class_and_members_pass(self):
        mapping = (f"{MODEL} -> {MODEL}:\n"
                   "    java.lang.String type -> type\n"
                   "    1:1:void <init>():5:5 -> <init>\n"
                   "    2:2:java.lang.String getType():9:9 -> getType\n")
        self.assertEqual([], self.check(mapping))

    def test_renamed_class_is_reported(self):
        problems = self.check(f"{MODEL} -> rn4:\n    java.lang.String type -> type\n")
        self.assertEqual([f"RENAMED  {MODEL} -> rn4"], problems)

    def test_renamed_field_is_reported(self):
        problems = self.check(f"{MODEL} -> {MODEL}:\n    java.lang.String type -> b\n")
        self.assertEqual([f"RENAMED  {MODEL}.type -> b"], problems)

    def test_renamed_getter_is_reported(self):
        problems = self.check(f"{MODEL} -> {MODEL}:\n    1:1:int getValue():3:3 -> a\n")
        self.assertEqual([f"RENAMED  {MODEL}.getValue -> a"], problems)

    def test_removed_class_is_reported(self):
        problems = self.check("com.other.Thing -> a:\n")
        self.assertEqual([f"REMOVED  {MODEL}  (R8 dropped the class)"], problems)

    def test_compiler_generated_nest_accessors_may_be_renamed(self):
        mapping = (f"{MODEL} -> {MODEL}:\n"
                   "    1:1:java.lang.String -$$Nest$fgettype(Inner):4:4 -> a\n")
        self.assertEqual([], self.check(mapping))

    def test_comment_lines_in_mapping_are_ignored(self):
        mapping = f"# compiler: R8\n{MODEL} -> {MODEL}:\n    # {{\"id\":\"x\"}}\n"
        self.assertEqual([], self.check(mapping))


if __name__ == "__main__":
    unittest.main()
