Yes. Based on everything we actually encountered—including the `invalid_signature` issue, the `uid` NameFormat problem, the IIS Site ID, ARR/URL Rewrite setup, the failed `{uid}` rewrite-variable approach, and the working Shibboleth header approach—I would rewrite the reference guide as follows.

This guide is specifically for your architecture:

```
                         Keycloak
                  https://localhost:9991
                  Realm: MySecurityRealm
                           ▲
                           │ SAML
                           ▼
Browser ─────────────► IIS :8088
                           │
                     Shibboleth SP
                           │
                    uid = username
                           │
                       IIS ARR
                           │ HTTP reverse proxy
                           ▼
                Spring Boot 127.0.0.1:9091
                     NO SAML CODE
```

The important architectural rule is:

> **Keycloak talks SAML to Shibboleth. Spring Boot does not talk SAML to Keycloak.**

---

# Part 1 — Components and URLs

For this example:

| Component               | Setting                                                       |
| ----------------------- | ------------------------------------------------------------- |
| Keycloak                | `https://localhost:9991`                                      |
| Realm                   | `MySecurityRealm`                                             |
| Keycloak IdP Entity ID  | `https://localhost:9991/realms/MySecurityRealm`               |
| Keycloak SAML endpoint  | `https://localhost:9991/realms/MySecurityRealm/protocol/saml` |
| IIS                     | `http://localhost:8088`                                       |
| Shibboleth SP Entity ID | `http://localhost:8088/shibboleth`                            |
| ACS                     | `http://localhost:8088/Shibboleth.sso/SAML2/POST`             |
| Shibboleth Session      | `http://localhost:8088/Shibboleth.sso/Session`                |
| Shibboleth Metadata     | `http://localhost:8088/Shibboleth.sso/Metadata`               |
| Spring Boot             | `http://127.0.0.1:9091`                                       |
| IIS Site ID             | `2`                                                           |

The final user-facing URL is:

```
http://localhost:8088/
```

Users should **not** normally access:

```
http://localhost:9091/
```

---

# Part 2 — Enable IIS

On Windows open:

```
Turn Windows features on or off
```

Enable:

```
Internet Information Services
    Web Management Tools
        IIS Management Console

    World Wide Web Services
        Common HTTP Features
            Default Document
            HTTP Errors
            Static Content

        Application Development Features
            ISAPI Extensions
            ISAPI Filters

        Security
            Request Filtering
```

You do not need ASP.NET for the Spring application.

After installation run:

```
inetmgr
```

IIS Manager should open.

---

# Part 3 — Install Shibboleth SP

Install Shibboleth Native SP for Windows with IIS integration enabled.

In our environment the installation directory is:

```
C:\Projects\Shibboleth_As_SP\shibboleth-sp\
```

Important directories:

```
C:\Projects\Shibboleth_As_SP\shibboleth-sp\
│
├── etc\shibboleth\
│      ├── shibboleth2.xml
│      ├── attribute-map.xml
│      └── keycloak-metadata.xml
│
├── sbin64\
│      └── shibd.exe
│
└── var\log\shibboleth\
       └── shibd.log
```

Make sure the Shibboleth service is running.

Validate configuration with:

```
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64

shibd.exe -check
```

Do this **every time you make a significant Shibboleth configuration change**.

---

# Part 4 — Create the IIS site

Create:

```
C:\Projects\Shibboleth_As_SP\iis-test
```

In IIS Manager:

```
Sites
    → Add Website
```

Use:

```
Site name:
ShibbolethDemo

Physical path:
C:\Projects\Shibboleth_As_SP\iis-test

Type:
http

Port:
8088
```

Your site becomes:

```
http://localhost:8088
```

Check its IIS Site ID:

```
Sites
 → ShibbolethDemo
 → Advanced Settings
 → ID
```

In our setup:

```
ID = 2
```

This number matters to Shibboleth.

---

# Part 5 — Configure Keycloak metadata for Shibboleth

Keycloak's IdP metadata is:

```
https://localhost:9991/realms/MySecurityRealm/protocol/saml/descriptor
```

Download/save that XML as:

```
C:\Projects\Shibboleth_As_SP\shibboleth-sp\etc\shibboleth\keycloak-metadata.xml
```

Then configure Shibboleth to use it.

In `shibboleth2.xml`:

```
<MetadataProvider
    type="XML"
    path="keycloak-metadata.xml"/>
```

For production, periodically refreshed remote metadata or another appropriately managed metadata mechanism may be preferable; the local file is simple and deterministic for this setup.

---

# Part 6 — Configure Shibboleth SP

The important pieces of `shibboleth2.xml` are the following.

## ApplicationDefaults

Use:

```
<ApplicationDefaults
    entityID="http://localhost:8088/shibboleth"
    REMOTE_USER="uid">
```

Do not remove other attributes already present on your existing `<ApplicationDefaults>` element.

The SP Entity ID therefore is:

```
http://localhost:8088/shibboleth
```

---

# Part 7 — Configure Shibboleth SSO

Inside `<Sessions>` configure Keycloak:

```
<SSO entityID="https://localhost:9991/realms/MySecurityRealm">
    SAML2
</SSO>
```

Be careful about these two URLs.

The **IdP entity ID** is:

```
https://localhost:9991/realms/MySecurityRealm
```

The SAML protocol endpoint is:

```
https://localhost:9991/realms/MySecurityRealm/protocol/saml
```

They are not interchangeable.

For this local HTTP test environment:

```
<Sessions
    lifetime="28800"
    timeout="3600"
    relayState="ss:mem"
    checkAddress="false"
    handlerSSL="false">
```

For production, use HTTPS and revisit these development-oriented settings.

---

# Part 8 — Protect the IIS site

Configure the RequestMapper so requests to port 8088 require a Shibboleth session.

Conceptually:

```
<RequestMapper type="Native">
    <RequestMap>

        <Host
            name="localhost"
            scheme="http"
            port="8088"
            authType="shibboleth"
            requireSession="true"/>

    </RequestMap>
</RequestMapper>
```

This gives us:

```
Browser requests /
       ↓
Does Shibboleth session exist?
       ↓
       NO
       ↓
Redirect to Keycloak
       ↓
login
       ↓
SAML Response
       ↓
Shibboleth session
       ↓
allow application request
```

---

# Part 9 — Configure IIS Site ID in Shibboleth

This was important in our setup.

Find:

```
<ISAPI>
```

Configure the IIS site:

```
<ISAPI
    normalizeRequest="true"
    safeHeaderNames="true"
    useVariables="true"
    useHeaders="true">

    <Site
        id="2"
        name="localhost"
        scheme="http"
        port="8088"/>

</ISAPI>
```

The:

```
id="2"
```

must correspond to the actual IIS Site ID.

Shibboleth SP3 defaults `useVariables` to true and `useHeaders` to false. Its documentation says `useHeaders` should generally be avoided in favor of server variables. [Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2063696253/ISAPI?utm_source=chatgpt.com)

### Why are we using `useHeaders="true"`?

This deserves special attention.

Normally I would prefer:

```
useVariables="true"
useHeaders="false"
```

However, our architecture introduces an HTTP reverse-proxy boundary:

```
IIS
 ↓ HTTP
ARR
 ↓ HTTP
Spring/Tomcat
```

The Shibboleth IIS server variables were available inside IIS, but our testing demonstrated that URL Rewrite evaluated:

```
{uid}
```

too early to turn it into:

```
X-Remote-User
```

The result was:

```
x-remote-user=[]
```

By contrast, with:

```
useHeaders="true"
```

we proved that ARR forwards:

```
uid: yunpeng.li
```

to Spring.

So this guide documents the **working IIS/ARR/Spring solution**, while recognizing that Shibboleth's preferred native IIS mechanism is server variables. [Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2063696253/ISAPI?utm_source=chatgpt.com)

---

# Part 10 — Configure Keycloak SAML client

In Keycloak:

```
Realm:
MySecurityRealm
```

Create a SAML client.

Use:

```
Client type:
SAML

Client ID:
http://localhost:8088/shibboleth
```

The Client ID must correspond to the Shibboleth SP entityID:

```
entityID="http://localhost:8088/shibboleth"
```

Configure the ACS / processing URL:

```
http://localhost:8088/Shibboleth.sso/SAML2/POST
```

The important relationship is:

```
Keycloak Client ID
       =
Shibboleth entityID

http://localhost:8088/shibboleth
```

---

# Part 11 — Client signature issue we encountered

Initially we received:

```
Invalid requester
```

and the Keycloak log showed:

```
error="invalid_signature"
```

and:

```
VerificationException:
Invalid query param signature
```

The immediate troubleshooting fix was to disable:

```
Client signature required
```

on the Keycloak SAML client.

After that, login worked.

For a production environment, don't simply consider that the final security configuration. Properly exchange/configure the Shibboleth SP signing certificate with Keycloak and re-enable request-signature validation according to your organization's SAML policy.

For our development environment:

```
Client signature required = OFF
```

was useful to isolate the rest of the integration.

---

# Part 12 — Create the Keycloak `uid` mapper

We want:

```
Keycloak username
        ↓
SAML Attribute
        ↓
uid
```

In the dedicated client scope for:

```
http://localhost:8088/shibboleth
```

create a mapper:

```
Name:
uid

Mapper Type:
User Property

Property:
username

Friendly Name:
uid

SAML Attribute Name:
uid

SAML Attribute NameFormat:
Basic
```

Keycloak therefore sends approximately:

```
<saml:Attribute
    Name="uid"
    NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic">
```

---

# Part 13 — Configure Shibboleth `attribute-map.xml`

This was another major issue we discovered.

Originally we had:

```
<Attribute name="uid" id="uid"/>
```

but Shibboleth logged:

```
skipping SAML 2.0 Attribute with Name: uid,
Format: urn:oasis:names:tc:SAML:2.0:attrname-format:basic
```

The NameFormat had to match.

Use:

```
<Attribute
    name="uid"
    nameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic"
    id="uid"/>
```

This was the change that made Shibboleth recognize Keycloak's `uid`.

After editing:

```
cd /d C:\Projects\Shibboleth_As_SP\shibboleth-sp\sbin64

shibd.exe -check
```

Restart Shibboleth.

Then start a new browser session and authenticate again.

---

# Part 14 — Verify Shibboleth before adding Spring

Do **not** configure Spring until this works.

Visit:

```
http://localhost:8088/Shibboleth.sso/Session
```

Expected:

```
Session Expiration
...

Identity Provider:
https://localhost:9991/realms/MySecurityRealm

SSO Protocol:
urn:oasis:names:tc:SAML:2.0:protocol

Attributes

uid: 1 value(s)
```

If you see:

```
uid: 1 value(s)
```

then:

```
Keycloak → SAML → Shibboleth
```

is working.

For temporary diagnostics, the Session handler can be configured to show actual attribute values, but don't leave sensitive attribute display enabled unnecessarily.

---

# Part 15 — Install IIS URL Rewrite and ARR

Spring will run behind IIS, so IIS needs reverse-proxy functionality.

Install:

**IIS URL Rewrite Module 2**

[Microsoft IIS URL Rewrite Module](https://www.iis.net/downloads/microsoft/url-rewrite?utm_source=chatgpt.com)

and:

**Application Request Routing (ARR)**

[Microsoft IIS Application Request Routing](https://www.iis.net/downloads/microsoft/application-request-routing?utm_source=chatgpt.com)

ARR uses URL Rewrite for its routing decisions. [Microsoft Learn](https://learn.microsoft.com/en-us/iis/extensions/url-rewrite-module/reverse-proxy-with-url-rewrite-v2-and-application-request-routing?utm_source=chatgpt.com)

After installation close and reopen:

```
inetmgr
```

At server level you should see:

```
Application Request Routing Cache
URL Rewrite
```

---

# Part 16 — Enable ARR reverse proxy

In IIS Manager select the **server**, not the site:

```
LAPTOP-...
   ↓
Application Request Routing Cache
   ↓
Server Proxy Settings
```

Check:

```
☑ Enable proxy
```

Then:

```
Apply
```

ARR's proxy functionality is disabled by default and must be explicitly enabled. [Microsoft Learn](https://learn.microsoft.com/en-us/iis/extensions/url-rewrite-module/reverse-proxy-with-url-rewrite-v2-and-application-request-routing?utm_source=chatgpt.com)

---

# Part 17 — Create the IIS reverse-proxy rule

Go to:

```
Sites
  → ShibbolethDemo
  → URL Rewrite
  → Add Rule(s)
  → Blank Rule
```

Name:

```
Spring Boot Reverse Proxy
```

Match:

```
Requested URL:
Matches the Pattern

Using:
Regular Expressions

Pattern:
^(.*)$
```

Add a condition:

```
Condition input:
{URL}

Check:
Does Not Match the Pattern

Pattern:
^/Shibboleth\.sso(?:/|$)
```

This is extremely important.

We want:

```
/Shibboleth.sso/*
        ↓
Shibboleth
```

but:

```
/
 /user
 /api/*
 ...
        ↓
Spring Boot
```

For the action:

```
Action type:
Rewrite

Rewrite URL:
http://127.0.0.1:9091/{R:1}

☑ Append query string
☑ Stop processing subsequent rules
```

Microsoft documents this ARR + URL Rewrite pattern for forwarding public IIS requests to an internal application server. [Microsoft Learn](https://learn.microsoft.com/en-us/iis/extensions/url-rewrite-module/reverse-proxy-with-url-rewrite-v2-and-application-request-routing?utm_source=chatgpt.com)

---

# Part 18 — Important: Do NOT use our failed rewrite-variable solution

During troubleshooting we tried:

```
HTTP_X_REMOTE_USER = {uid}
```

and later:

```
HTTP_X_REMOTE_USER = {REMOTE_USER}
```

Both resulted in:

```
"x-remote-user":[]
```

Don't include that configuration in the final setup.

We established that URL Rewrite could create:

```
X-Remote-User
```

but `{uid}` was empty when the rewrite rule evaluated it.

Therefore remove:

```
HTTP_X_REMOTE_USER = {uid}
```

from the final reverse-proxy rule.

You also no longer need to add:

```
HTTP_X_REMOTE_USER
```

to URL Rewrite's Allowed Server Variables for this final configuration.

---

# Part 19 — What actually worked

With:

```
useHeaders="true"
```

the Java diagnostic endpoint showed:

```
"shibidentityprovider":
["https://localhost:9991/realms/MySecurityRealm"],

"shibapplicationid":
["default"],

"uid":
["yunpeng.li"]
```

That proves:

```
Keycloak
    ↓
SAML uid
    ↓
Shibboleth
    ↓
uid = yunpeng.li
    ↓
IIS
    ↓
ARR
    ↓
HTTP header
    ↓
Spring
```

Spring can therefore read:

```
request.getHeader("uid")
```

---

# Part 20 — Create Spring Boot application

Create a standard Spring Boot project.

You need:

```
Spring Web
```

You do **not** need:

```
Spring Security SAML2 Service Provider
```

and you do not need Spring SAML configuration.

Your `pom.xml` needs the normal Spring Web starter, for example:

```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

If using Thymeleaf pages, also add:

```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

There should be **no**:

```
spring-security-saml2-service-provider
```

for this architecture.

---

# Part 21 — Bind Spring to loopback only

This is a very important security control.

Use:

```
server:
  address: 127.0.0.1
  port: 9091
```

Now Spring listens on:

```
127.0.0.1:9091
```

rather than exposing port 9091 on every network interface.

The intended path is:

```
External client
      │
      X
      │ cannot directly access Spring
      │
      ▼
IIS
      │
      ▼
127.0.0.1:9091
```

---

# Part 22 — Read the authenticated user in Spring

For a simple controller:

```
package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(
            HttpServletRequest request,
            Model model) {

        String uid = request.getHeader("uid");

        if (uid == null || uid.isBlank()) {
            throw new IllegalStateException(
                "Missing Shibboleth authenticated uid"
            );
        }

        model.addAttribute("uid", uid);

        return "home";
    }
}
```

Then:

```
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Shibboleth Demo</title>
</head>

<body>

<h1>Shibboleth Login Successful</h1>

<p>
    Welcome,
    <strong th:text="${uid}">username</strong>
</p>

</body>
</html>
```

Expected:

```
Shibboleth Login Successful

Welcome, yunpeng.li
```

---

# Part 23 — Better Java design

Don't scatter:

```
request.getHeader("uid")
```

throughout the application.

Centralize it.

For example:

```
package com.example.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ShibbolethUser {

    public String getUsername(HttpServletRequest request) {

        String uid = request.getHeader("uid");

        if (uid == null || uid.isBlank()) {
            throw new IllegalStateException(
                "No authenticated Shibboleth uid"
            );
        }

        return uid;
    }
}
```

Then:

```
@Controller
public class HomeController {

    private final ShibbolethUser shibbolethUser;

    public HomeController(ShibbolethUser shibbolethUser) {
        this.shibbolethUser = shibbolethUser;
    }

    @GetMapping("/")
    public String home(
            HttpServletRequest request,
            Model model) {

        String username =
            shibbolethUser.getUsername(request);

        model.addAttribute("uid", username);

        return "home";
    }
}
```

That gives your application one place to manage the trusted upstream identity.

---

# Part 24 — `getRemoteUser()` is NOT the same thing

Don't expect this to work automatically:

```
request.getRemoteUser()
```

ARR is using HTTP to proxy the request. An HTTP header such as:

```
uid: yunpeng.li
```

does not automatically create a Java servlet authenticated principal.

So in this architecture:

```
request.getRemoteUser()
```

may return:

```
null
```

while:

```
request.getHeader("uid")
```

returns:

```
yunpeng.li
```

That is expected.

---

# Part 25 — Diagnostic endpoint

During setup, this Java endpoint was extremely useful:

```
@GetMapping("/user")
@ResponseBody
public Map<String, Object> user(
        HttpServletRequest request) {

    Map<String, Object> result =
        new LinkedHashMap<>();

    Enumeration<String> names =
        request.getHeaderNames();

    while (names != null &&
           names.hasMoreElements()) {

        String name = names.nextElement();

        result.put(
            name,
            Collections.list(
                request.getHeaders(name)
            )
        );
    }

    return result;
}
```

It allowed us to prove:

```
"uid":["yunpeng.li"]
```

and:

```
"shibapplicationid":["default"]
```

were arriving.

### But remove this endpoint afterward.

Our diagnostic output also exposed:

```
ShibSpoofCheck
ShibSessionID
```

and other Shibboleth internal information.

Do **not** expose an endpoint that dumps all request headers in production.

---

# Part 26 — Security of `useHeaders`

This is the biggest caveat in the final design.

Shibboleth's documentation says:

```
useVariables = true    ← preferred
useHeaders   = false   ← default
```

and specifically notes that `useHeaders` should be avoided when possible. [Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2063696253/ISAPI?utm_source=chatgpt.com)

We are using header propagation because of the practical IIS → ARR → HTTP → Spring boundary.

Therefore the **trust boundary must be protected**.

At minimum:

```
Spring :9091
      ↓
bind ONLY to
127.0.0.1
```

and:

```
Browser
   ↓
IIS
   ↓
Shibboleth must authenticate
   ↓
ARR
   ↓
Spring
```

Never expose:

```
9091
```

directly to other computers.

Also leave Shibboleth spoof checking enabled.

Do not disable spoof protection merely to make a header appear.

---

# Part 27 — Production HTTPS

Our development setup uses:

```
http://localhost:8088
```

For production use:

```
https://application.example.org
```

Then update all relevant identifiers consistently.

For example:

```
SP entityID:
https://application.example.org/shibboleth

ACS:
https://application.example.org/Shibboleth.sso/SAML2/POST
```

Keycloak must use those production URLs too.

Don't mix:

```
http://localhost:8088
```

development metadata with:

```
https://application.example.org
```

production metadata.

---

# Part 28 — Certificate/signature configuration for production

Our development setup temporarily disabled:

```
Client signature required
```

because Keycloak rejected the Shibboleth AuthnRequest with:

```
invalid_signature
```

For production, configure the Shibboleth SP signing certificate correctly in Keycloak and re-enable the desired signature verification.

The four certificate roles should be kept conceptually separate:

```
1. Keycloak HTTPS/TLS certificate

2. Keycloak SAML signing certificate
   Keycloak signs SAML assertions/responses

3. Shibboleth SP signing certificate
   Shibboleth signs SAML requests

4. Smart-card/user certificate
   if X.509 authentication is added later
```

Don't reuse or confuse these roles.

---

# Part 29 — Final startup/test sequence

When troubleshooting, always test from the inside out.

**1. Test Spring:**

```
http://127.0.0.1:9091/
```

The application should be running.

Authentication information is not necessarily present because you're bypassing IIS.

**2. Test Shibboleth:**

```
http://localhost:8088/Shibboleth.sso/Session
```

You want:

```
uid: 1 value(s)
```

**3. Test the complete application:**

Use a new InPrivate browser and visit:

```
http://localhost:8088/
```

Expected sequence:

```
localhost:8088
      ↓
Shibboleth sees no session
      ↓
Keycloak login
      ↓
SAMLResponse
      ↓
Shibboleth session
      ↓
uid = yunpeng.li
      ↓
ARR
      ↓
Spring
      ↓
Welcome, yunpeng.li
```

---

# Part 30 — Troubleshooting cheat sheet

| Symptom                                                             | Likely cause                                               |
| ------------------------------------------------------------------- | ---------------------------------------------------------- |
| Keycloak says `Invalid requester`                                   | Client ID/entityID mismatch or signature validation        |
| Keycloak log says `invalid_signature`                               | Shibboleth request signature not accepted                  |
| Shibboleth session works but no `uid`                               | Mapper/attribute mapping problem                           |
| `shibd.log` says `skipping SAML 2.0 Attribute ... Format ... basic` | `attribute-map.xml` NameFormat mismatch                    |
| `/Shibboleth.sso/Session` shows `uid: 1 value(s)`                   | Keycloak → Shibboleth attribute flow works                 |
| IIS gives 502                                                       | Spring isn't running, wrong backend URL, or ARR problem    |
| Spring works directly but not through IIS                           | ARR/URL Rewrite problem                                    |
| `x-remote-user=[]`                                                  | `{uid}` rewrite-variable approach isn't working            |
| Java sees `"uid":["yunpeng.li"]`                                    | Shibboleth → IIS → ARR → Spring identity propagation works |
| `request.getRemoteUser()` is null                                   | Expected with this HTTP-header proxy architecture          |
| `/Shibboleth.sso/Session` gets sent to Spring                       | Rewrite rule isn't excluding `/Shibboleth.sso/*`           |
| Spring can be reached remotely on 9091                              | Fix `server.address=127.0.0.1` / firewall immediately      |

---

# Part 31 — Final configuration summary

The important Shibboleth settings are essentially:

```
<ApplicationDefaults
    entityID="http://localhost:8088/shibboleth"
    REMOTE_USER="uid">

    <Sessions
        lifetime="28800"
        timeout="3600"
        relayState="ss:mem"
        checkAddress="false"
        handlerSSL="false">

        <SSO entityID="https://localhost:9991/realms/MySecurityRealm">
            SAML2
        </SSO>

    </Sessions>

    <MetadataProvider
        type="XML"
        path="keycloak-metadata.xml"/>

</ApplicationDefaults>
```

plus:

```
<ISAPI
    normalizeRequest="true"
    safeHeaderNames="true"
    useVariables="true"
    useHeaders="true">

    <Site
        id="2"
        name="localhost"
        scheme="http"
        port="8088"/>

</ISAPI>
```

and:

```
<Attribute
    name="uid"
    nameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic"
    id="uid"/>
```

IIS then does:

```
localhost:8088/*
        │
        ├── /Shibboleth.sso/* → Shibboleth
        │
        └── everything else
                   ↓
          http://127.0.0.1:9091/*
```

Spring does:

```
server:
  address: 127.0.0.1
  port: 9091
```

and retrieves:

```
String username =
    request.getHeader("uid");
```

The result is:

```
Browser
   │
   ▼
IIS :8088
   │
   ▼
Shibboleth SP
   │
   │ SAML
   ▼
Keycloak
   │
   │ authenticated user
   ▼
Shibboleth
   │
   │ uid = yunpeng.li
   ▼
IIS / ARR
   │
   │ HTTP proxy
   ▼
Spring :9091
   │
   ▼
Welcome, yunpeng.li
```

That is the version of the instructions I would keep as your reference because it reflects **what actually worked in your environment**, including the important distinction between Shibboleth's preferred IIS server-variable model and the header bridge we're using specifically because Spring is behind ARR. [Atlassian](https://shibboleth.atlassian.net/wiki/spaces/SP3/pages/2063696253/ISAPI?utm_source=chatgpt.com)
