import io, sys
f, m = sys.argv[1], sys.argv[2]
t = io.open(f, encoding='utf-8').read()
if "<module>" + m + "</module>" in t:
    print("  ✓ 已注册: " + m)
    sys.exit(0)
t = t.replace("</modules>", "    <module>" + m + "</module>\n    </modules>", 1)
io.open(f, "w", encoding="utf-8").write(t)
print("  ✓ 注册: " + m + " -> " + f)
