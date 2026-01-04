import textwrap
from pathlib import Path
from tempfile import TemporaryDirectory

from biz.utils.java_context_analyzer import JavaContextAnalyzer


def _new_analyzer():
    # 跳过 __init__ 里昂贵的编译逻辑
    return JavaContextAnalyzer.__new__(JavaContextAnalyzer)


def test_extract_method_from_body_only_diff():
    diff_text = textwrap.dedent(
        """
        diff --git a/src/main/java/com/example/Demo.java b/src/main/java/com/example/Demo.java
        index 0000000..1111111 100644
        --- a/src/main/java/com/example/Demo.java
        +++ b/src/main/java/com/example/Demo.java
        @@ -2,4 +2,5 @@ public class Demo {
             public void foo() {
        -        int a = 1;
        +        int a = 2;
        +        System.out.println(a);
             }
         }
        """
    ).strip("\n")

    with TemporaryDirectory() as tmp:
        java_file = Path(tmp) / "src/main/java/com/example/Demo.java"
        java_file.parent.mkdir(parents=True, exist_ok=True)
        java_file.write_text(
            textwrap.dedent(
                """
                package com.example;
                public class Demo {
                    public void foo() {
                        int a = 2;
                        System.out.println(a);
                    }
                }
                """
            ).strip("\n"),
            encoding="utf-8",
        )

        analyzer = _new_analyzer()
        changed = analyzer._extract_changed_methods(
            tmp, diff_text, ["src/main/java/com/example/Demo.java"]
        )

        assert changed == {"src/main/java/com/example/Demo.java": ["foo"]}






