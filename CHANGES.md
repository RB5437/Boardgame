# Changes Applied to This Fork

This file documents every fix applied to the original `jaiswaladi246/Boardgame`
project before building/deploying it, and why each change was necessary.

| # | File | What Changed | Why |
|---|------|--------------|-----|
| 1 | `pom.xml` | `spring-boot-starter-parent` version `2.5.6` → `3.5.13` | 2.5.6 is from 2021 and unsupported. Spring Boot 3.5's own OSS support window ends June 30, 2026, so 3.5.13 is the latest safe target without a major-version jump to 4.x. |
| 2 | `pom.xml` | `<java.version>` `11` → `17` | Spring Boot 3.x requires Java 17 minimum. This also fixes a hidden mismatch: the Jenkinsfile already requested `jdk 'jdk17'`, but pom.xml was still declaring Java 11. |
| 3 | `pom.xml` | `thymeleaf-extras-springsecurity5` → `thymeleaf-extras-springsecurity6` | Spring Boot 3.x ships Spring Security 6. The `springsecurity5` Thymeleaf extras module is incompatible with it. |
| 4 | `pom.xml` | Removed duplicate `jacoco-maven-plugin` entry from `<dependencies>` | Jacoco is a build-time plugin, not a runtime dependency. It was already (correctly) declared in `<plugins>` — having it in both places is invalid Maven usage. |
| 5 | `pom.xml` | `jacoco.version` `0.8.7` → `0.8.12` | 0.8.7 does not reliably support bytecode produced by newer Java/Spring Boot builds. |
| 6 | `pom.xml` | Commented out `<distributionManagement>` pointing to `13.127.177.61:8081` | That Nexus server belongs to the original course creator, not this fork. Left as a documented template instead of a dead/inaccessible URL. |
| 7 | `src/main/java/com/javaproject/security/SecurityConfig.java` | Rewrote entire class — removed `WebSecurityConfigurerAdapter` | **This was the build-breaking issue.** `WebSecurityConfigurerAdapter` was deprecated in Spring Security 5.7 and fully removed in Spring Security 6 (used by Spring Boot 3.x). The class would not compile at all on the upgraded dependencies. Replaced with the modern `SecurityFilterChain` bean + `AuthenticationManager` bean pattern. Demo user seeding (`bugs`/`bunny`, `daffy`/`duck`) was moved into an `ApplicationRunner` bean since the old `AuthenticationManagerBuilder.withDefaultSchema().withUser(...)` chain no longer exists either. |
| 8 | `src/main/java/com/javaproject/security/LoggingAccessDeniedHandler.java` | `javax.servlet.*` → `jakarta.servlet.*` | Spring Boot 3.x moved from the Java EE `javax.*` namespace to the Eclipse Foundation's `jakarta.*` namespace (Jakarta EE 9+). Servlet API imports must use `jakarta.servlet`. |
| 9 | `deployment-service.yaml` | `image: adijaiswal/boardshack:latest` → `image: ritik2909/boardgame:latest` | **Critical fix.** The original manifest pointed at the course creator's own Docker Hub image. Deploying this as-is would always pull *his* image, never your own build — meaning your CI/CD pipeline would have no visible effect on what's actually running. |
| 10 | `Dockerfile` | `eclipse-temurin:17-jre-alpine` → `eclipse-temurin:17-jre-alpine-3.21` | The unqualified `-alpine` tag has stopped receiving updates upstream; new Alpine patch builds now ship under version-specific tags. Pinning to `-alpine-3.21` ensures the base OS layer keeps getting security patches. |

## What Was NOT Changed (and why)

- `javax.sql.DataSource` imports in `DatabaseAccess.java` and `SecurityConfig.java`
  were left as-is. `javax.sql` is part of the core JDK itself (not Jakarta EE),
  so it did not need to move to `jakarta.*`.
- `Jenkinsfile` was left with only 3 stages (Compile, Test, Build) intentionally —
  the DevSecOps stages (SonarQube, Trivy, Docker, Kubernetes deploy) are being
  added incrementally as part of Day 71-75 of the 90-day plan, not bulk-generated.
- `schema.sql` (board game + review tables) was left untouched — it has nothing
  to do with the Spring Security upgrade and was already correct.

## How to Verify These Fixes Locally

```bash
# 1. Confirm Java 17 is active
java -version

# 2. Build - this is the real test that the Spring Security 6 rewrite compiles
./mvnw clean package

# 3. Run locally
./mvnw spring-boot:run
# Visit http://localhost:8080
# Login with bugs/bunny (user) or daffy/duck (manager)

# 4. Before deploying to Kubernetes, confirm the image name is yours
grep "image:" deployment-service.yaml
```
