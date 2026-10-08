# -*- coding: utf-8 -*-
"""append service-<svc> into aggregator(all-in-one) <dependencies> tail (idempotent)."""
import io, sys

svc, f = sys.argv[1], sys.argv[2]
t = io.open(f, encoding="utf-8").read()
if ("<artifactId>service-" + svc + "</artifactId>") in t:
    print("  ok exists")
    sys.exit(0)
lines = [
    "        <dependency>",
    "            <groupId>io.triform</groupId>",
    "            <artifactId>service-" + svc + "</artifactId>",
    "            <version>${project.version}</version>",
    "        </dependency>",
    "",
]
idx = t.find("</dependencies>")
assert idx > 0, "no </dependencies>"
io.open(f, "w", encoding="utf-8", newline="").write(t[:idx] + "\n".join(lines) + t[idx:])
print("  ok aggregated service-" + svc)
