# 08. Forensic Security Audit & OWASP Assessment

> **Scope:** Comprehensive application security audit across authentication, authorization, injection vectors, data integrity, rate limiting, and FinTech compliance.

---

## 1. Security Posture Summary

| Category | Evaluation | Forensic Verdict |
|---|---|---|
| **SQL Injection** | **PROTECTED** | Flyway migrations use parameterized DDL; Spring Data JPA uses parameterized PreparedStatements (`:id`, `:batchSize`). Zero string concatenation in SQL. |
| **Cross-Site Scripting (XSS)** | **PROTECTED** | Frontend `app.js` strictly sanitizes dynamic HTML insertions using `escapeHtml()` converting `&`, `<`, `>`, `"`. |
| **Cross-Site Request Forgery (CSRF)** | **PROTECTED (Stateless)** | Disabled via `csrf.disable()`, which is industry standard for stateless REST APIs using Bearer/API Key authentication without session cookies. |
| **Double-Spending & Race Conditions**| **PROTECTED** | Pessimistic database row locks (`SELECT ... FOR UPDATE`) combined with two-tier Redis/Postgres idempotency. |
| **Cryptographic Token Verification** | **VULNERABLE (Mock Mode)** | `MockJwtAuthenticationFilter` parses Base64 claims without verifying cryptographic HMAC/RSA signatures. |
| **API Key Entropy & Hardcoding** | **VULNERABLE (Substring Matching)** | `ApiKeyAuthenticationFilter` checks if the key contains `"admin"` or `"finance"` substring instead of checking high-entropy hashed keys. |
| **Rate Limiting & DoS Protection** | **MISSING** | No IP-based rate limiting or Bucket4j token bucket configured on `/api/v1/payments`. |

---

## 2. Detailed Vulnerability Findings & Remediation

### Finding SEC-01: Mock JWT Signature Bypass (CRITICAL)
- **Affected File:** `src/main/java/com/aman/payment/infrastructure/security/MockJwtAuthenticationFilter.java`
- **Severity:** **HIGH (Demo / Mock Limitation)**
- **Explanation:**  
  The filter decodes the JWT payload from Base64:
  ```java
  byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
  JsonNode claims = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
  ```
  However, it does **not** verify the cryptographic signature against an RSA Public Key or HMAC secret!
- **Exploitation Scenario:**  
  An attacker can craft an un-signed JWT token with arbitrary claims `{"sub": "attacker", "roles": ["ROLE_FINANCE_ADMIN"]}`. The filter parses the payload, trusts the `roles` node, and grants the attacker full administrative access to `/api/v1/ledger/reconcile`.
- **Recommended Enterprise Fix:**  
  Replace `MockJwtAuthenticationFilter` with Spring Security's native OAuth2 Resource Server:
  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>
  ```
  In `application.yml`, configure JWKS endpoint:
  ```yaml
  spring:
    security:
      oauth2:
        resourceserver:
          jwt:
            jwk-set-uri: https://auth.company.com/.well-known/jwks.json
  ```
- **Interview Defense Strategy:**  
  *"For local demonstration and offline interview testing without requiring an external Okta or Keycloak server, we implemented `MockJwtAuthenticationFilter`. In production, this is swapped for Spring Security's native OAuth2 Resource Server validating RS256 signatures against our corporate OIDC identity provider."*

---

### Finding SEC-02: Predictable API Key Substring Matching (HIGH)
- **Affected File:** `src/main/java/com/aman/payment/infrastructure/security/ApiKeyAuthenticationFilter.java`
- **Severity:** **HIGH**
- **Explanation:**  
  The filter assigns administrative privileges using simple string containment:
  ```java
  String lower = apiKey.toLowerCase();
  if (lower.contains("admin") || lower.contains("finance")) {
      // Grants ROLE_FINANCE_ADMIN
  }
  ```
- **Exploitation Scenario:**  
  Any client sending `X-API-KEY: test-finance` or `X-API-KEY: my-admin-key` is automatically granted `ROLE_FINANCE_ADMIN`.
- **Recommended Enterprise Fix:**  
  Store salted, SHA-256 hashes of client API keys in an `api_keys` database table. Compare hashes in constant time (`MessageDigest.isEqual`) to prevent timing attacks.

---

### Finding SEC-03: Lack of Rate Limiting on Payment Ingestion (MEDIUM)
- **Affected File:** `src/main/java/com/aman/payment/presentation/v1/PaymentController.java`
- **Severity:** **MEDIUM**
- **Explanation:**  
  There is no rate-limiting filter (e.g. Bucket4j or Redis Token Bucket). While the idempotency key stops identical requests, an attacker can flood the API with unique keys, exhausting the database connection pool (`HikariPool`).
- **Recommended Enterprise Fix:**  
  Implement a Redis-backed Token Bucket rate limiter in a `RateLimitingFilter`:
  ```java
  // Allow maximum 100 requests per minute per IP or API key
  String redisKey = "rate_limit:" + clientIp;
  Long currentCount = redisTemplate.opsForValue().increment(redisKey);
  if (currentCount == 1) redisTemplate.expire(redisKey, Duration.ofMinutes(1));
  if (currentCount > 100) return ResponseEntity.status(429).body("Rate limit exceeded");
  ```

---

### Finding SEC-04: Insecure Direct Object Reference (IDOR) Potential (MEDIUM)
- **Affected File:** `src/main/java/com/aman/payment/presentation/v1/AccountController.java`
- **Severity:** **MEDIUM**
- **Explanation:**  
  Endpoints like `GET /api/v1/accounts/{accountId}` and `POST /api/v1/accounts/{accountId}/withdraw` accept an arbitrary `accountId` UUID in the path without validating whether the currently authenticated user is the legal owner of that account.
- **Recommended Enterprise Fix:**  
  Extract the caller's identity (`principal = SecurityContextHolder.getContext().getAuthentication().getName()`) and assert ownership:
  ```java
  if (!account.getOwnerName().equals(principal) && !hasAdminRole()) {
      throw new AccessDeniedException("You do not own this account");
  }
  ```

---

### Finding SEC-05: Missing HTTPS / TLS Termination Configuration (LOW)
- **Affected File:** `src/main/resources/application.yml`
- **Severity:** **LOW (Environment Context)**
- **Explanation:**  
  The service runs over unencrypted HTTP on port `8081`. In production, financial credentials (`X-API-KEY`, Bearer tokens) can be sniffed if transmitted over plaintext.
- **Recommended Enterprise Fix:**  
  In modern cloud architectures, TLS is terminated at the AWS Application Load Balancer (ALB) or Cloudflare edge, forwarding HTTP to the container within a private VPC. If end-to-end encryption is required, configure Spring Boot with an SSL keystore:
  ```yaml
  server:
    ssl:
      enabled: true
      key-store: classpath:keystore.p12
      key-store-password: ${SSL_KEYSTORE_PASSWORD}
  ```
