# demo-repo

A tiny Java project with intentional bugs, used to demo the AI Code Review Council.

## Bugs included
- **SQL injection** in `UserService.findByName` (string concat)
- **N+1 query** in `UserService.getOrdersForUsers` (loop with per-row query)
- **Resource leak** in `UserService.findAll` (no try-with-resources)
- **Hardcoded secret** in `UserService.DB_PASSWORD`

## Run the council on it
```bash
# 1. Start the council from this project's root
cd ..
java -jar target/lumen.jar validate --config=council.yaml

# 2. (when you have ANTHROPIC_API_KEY set)
java -jar target/lumen.jar run --config=council.yaml
# → should report: 3-4 findings (1 critical SQL injection, 1 major N+1, 1 major secret, 1 minor resource leak)
```

## Expected findings (illustrative)

| File | Line | Severity | Issue |
|---|---|---|---|
| `UserService.java` | 15-18 | critical | SQL injection via string concatenation |
| `UserService.java` | 22-34 | major | N+1 query: per-user SELECT inside loop |
| `UserService.java` | 38-46 | major | Resource leak: no try-with-resources |
| `UserService.java` | 50 | major | Hardcoded password `admin123` |
