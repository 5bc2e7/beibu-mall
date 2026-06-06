# QA Test Report — mall-user-service (Re-Test After Fixes)

**Date:** 2026-06-02 (re-test)  
**Service:** user-service (port 9001)  
**Infrastructure:** MySQL 8.0 (localhost:3307), Redis 7 (localhost:6379), Nacos 3.2.1 (localhost:8848)  
**Gateway:** NOT RUNNING — cannot start due to WebFlux/WebMVC dependency conflict (see BUG-5)

---

## Fixes Applied

1. **`UserApplication.java`** — Added `scanBasePackages = "com.beibu.mall"` to scan GlobalExceptionHandler from mall-common
2. **`mall-gateway/application.yml`** — Added routes for user-service (`/api/user/**`) and address-service (`/api/address/**`)
3. **`WebMvcConfig.java`** — Knife4j interceptor exclusions already present (`/doc.html`, `/webjars/**`, `/v3/api-docs/**`, `/swagger-resources/**`)

---

## Re-Test Summary

| Category | Passed | Failed | Total |
|----------|--------|--------|-------|
| GlobalExceptionHandler | 4 | 0 | 4 |
| Validation Errors | 2 | 0 | 2 |
| Address Service Errors | 0 | 2 | 2 |
| Knife4j | 1 | 1 | 2 |
| Existing Functionality | 3 | 0 | 3 |
| **Total** | **10** | **3** | **13** |

---

## Scenario 1: GlobalExceptionHandler Now Working ✅

All BizExceptions now return proper `Result` JSON format instead of raw Spring 500 errors.

| # | Test | Expected | Actual | Status |
|---|------|----------|--------|--------|
| 1 | POST /api/user/register with duplicate username | `{"code":40001,"msg":"用户名已存在","data":null}` | `{"code":40001,"msg":"用户名已存在","data":null}` | ✅ PASS |
| 2 | POST /api/user/register with duplicate phone | `{"code":40002,"msg":"手机号已被注册","data":null}` | `{"code":40002,"msg":"手机号已被注册","data":null}` | ✅ PASS |
| 3 | POST /api/user/login with wrong password | `{"code":40003,"msg":"用户名或密码错误","data":null}` | `{"code":40003,"msg":"用户名或密码错误","data":null}` | ✅ PASS |
| 4 | POST /api/user/login with non-existent user | `{"code":40003,"msg":"用户名或密码错误","data":null}` | `{"code":40003,"msg":"用户名或密码错误","data":null}` | ✅ PASS |

**Root cause of original failure:** `UserApplication` was in `com.beibu.mall.user` — only scanned that package. `GlobalExceptionHandler` in `com.beibu.mall.common.config` was invisible. Fix: `scanBasePackages = "com.beibu.mall"`.

---

## Scenario 2: Validation Errors Now Return JSON ✅

`@Valid` / `MethodArgumentNotValidException` now caught by GlobalExceptionHandler and returned as `Result.fail(400, msg)`.

| # | Test | Expected | Actual | Status |
|---|------|----------|--------|--------|
| 5 | POST /api/user/register with empty username | `{"code":400,"msg":"用户名不能为空","data":null}` | `{"code":400,"msg":"用户名不能为空","data":null}` | ✅ PASS |
| 6 | POST /api/user/register with invalid phone | `{"code":400,"msg":"手机号格式不正确","data":null}` | `{"code":400,"msg":"手机号格式不正确","data":null}` | ✅ PASS |

---

## Scenario 3: Address Service Errors ❌

**BUG-2 (Missing `-parameters` flag) is NOT fixed.** The `@PathVariable Long id` in `AddressController` cannot resolve parameter names via reflection. Spring 6.x throws `IllegalArgumentException` before the method is even invoked, so the `BizException(40011, "地址不存在")` is never reached.

| # | Test | Expected | Actual | Status |
|---|------|----------|--------|--------|
| 7 | GET /api/address/{non-existent-id} | `{"code":40011,"msg":"地址不存在","data":null}` | `{"code":500,"msg":"系统繁忙，请稍后重试","data":null}` | ❌ FAIL |
| 8 | DELETE /api/address/{non-existent-id} | `{"code":40011,"msg":"地址不存在","data":null}` | `{"code":500,"msg":"系统繁忙，请稍后重试","data":null}` | ❌ FAIL |

**Error from logs:**
```
java.lang.IllegalArgumentException: Name for argument of type [java.lang.Long] not specified,
and parameter name information not available via reflection.
Ensure that the compiler uses the '-parameters' flag.
```

**Fix required:** Add to parent `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
    </configuration>
</plugin>
```

---

## Scenario 4: Knife4j Accessibility ⚠️

| # | Test | Expected | Actual | Status |
|---|------|----------|--------|--------|
| 9 | GET /doc.html | 200 | 200 OK | ✅ PASS |
| 10 | GET /swagger-resources/** | 200 | 500 (NoResourceFoundException) | ❌ FAIL |

**Note:** `/swagger-resources/**` is a Swagger 2.x path. With Knife4j OpenAPI3 (springdoc), the equivalent is `/v3/api-docs/**`. The `/doc.html` endpoint works correctly. The interceptor exclusions are in place but irrelevant — the JWT interceptor only matches `/api/**`, so non-API paths like `/doc.html` and `/swagger-resources/**` are never intercepted anyway.

The 500 on `/swagger-resources/configuration/ui` is a `NoResourceFoundException` — this path doesn't exist in the OpenAPI3 setup. This is expected behavior for Knife4j v4.x with springdoc.

---

## Scenario 5: Existing Functionality Still Works ✅

| # | Test | Expected | Actual | Status |
|---|------|----------|--------|--------|
| 11 | POST /api/user/register with valid data | `{"code":200,"msg":"success","data":null}` | `{"code":200,"msg":"success","data":null}` | ✅ PASS |
| 12 | POST /api/user/login with valid credentials | `{"code":200,"msg":"success","data":{...token...}}` | `{"code":200,"msg":"success","data":{"userId":...,"username":"qatest_fresh","nickname":"qatest_fresh","token":"eyJ..."}}` | ✅ PASS |
| 13 | GET /api/user/me with valid token | User info JSON | `{"code":200,"msg":"success","data":{"id":...,"username":"qatest_fresh","phone":"13900000001",...}}` | ✅ PASS |

---

## BUG-5: Gateway Cannot Start (NEW — CRITICAL)

**Root Cause:** `mall-gateway` depends on `mall-common`, which brings in `spring-boot-starter-web` (WebMVC). Spring Cloud Gateway requires WebFlux. These two cannot coexist.

**Error:**
```
Please set spring.main.web-application-type=reactive or remove spring-boot-starter-web dependency.
```

**Impact:** Gateway (port 9000) cannot start. All gateway route tests are impossible. The gateway routes configured in `application.yml` (user-service and address-service) are untestable.

**Fix options:**
1. Remove `mall-common` dependency from gateway (breaks shared code access)
2. Exclude `spring-boot-starter-web` from `mall-common` in gateway's pom.xml
3. Make `mall-common` not pull in `spring-boot-starter-web` directly (use optional/provided scope)

---

## Unfixed Bugs From Original Report

| Bug | Severity | Status | Notes |
|-----|----------|--------|-------|
| BUG-1: GlobalExceptionHandler not scanned | CRITICAL | ✅ FIXED | `scanBasePackages = "com.beibu.mall"` applied |
| BUG-2: Missing `-parameters` flag | CRITICAL | ❌ NOT FIXED | Address endpoints still return 500 |
| BUG-3: No password length validation | MEDIUM | ❌ NOT FIXED | Not part of this fix scope |
| BUG-4: XSS in input fields | LOW | ❌ NOT FIXED | Not part of this fix scope |
| BUG-5: Gateway WebFlux conflict | CRITICAL | 🆕 NEW | Gateway cannot start |

---

## Test Environment Notes

- **User-service** was restarted with `mvn spring-boot:run` to pick up the `scanBasePackages` fix
- **Gateway** was attempted but failed to start (BUG-5)
- All tests run against user-service directly on port 9001
- Docker containers (MySQL, Redis, Nacos) all healthy and running
