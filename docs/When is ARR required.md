**Question: Some applications don't use ARR, are they .net applications? When ARR is required?**

Yes. Many applications that use IIS without ARR are ASP.NET or ASP.NET Framework applications. But not all of them.

The key factor is how the application is hosted, not which programming language it uses.

## 1. When is ARR required?

ARR is needed when you choose to use IIS as a reverse proxy to an application running on a separate HTTP server.

Compare these three architectures.

No ARR needed

A. ASP.NET Framework hosted in IIS

Browser

IIS

Shibboleth ISAPI

ASP.NET Framework App

Runs inside IIS worker process

ARR used

B. Spring Boot behind IIS

Browser

IIS

Shibboleth ISAPI

URL Rewrite + ARR

Spring Boot

Tomcat :9091

No ARR needed

C. ASP.NET Core using IIS ASP.NET Core Module (ANCM)

Browser

IIS + ASP.NET Core Module

In-process hosting or built-in out-of-process forwarding

ASP.NET Core application

Notice that ASP.NET Core can run in a separate process without ARR because its IIS hosting module provides the integration and forwarding mechanism.

## 2. Compare common applications

| Application type              | How IIS serves it                           | ARR needed?                                         |
| ----------------------------- | ------------------------------------------- | --------------------------------------------------- |
| ASP.NET Framework             | Runs in IIS worker process                  | No                                                  |
| ASP.NET Core (in-process)     | Runs inside IIS worker process              | No                                                  |
| ASP.NET Core (out-of-process) | IIS ASP.NET Core Module forwards to Kestrel | No                                                  |
| Classic ASP                   | IIS executes ASP pages                      | No                                                  |
| Static HTML website           | IIS serves files directly                   | No                                                  |
| Spring Boot + embedded Tomcat | IIS proxies to Tomcat                       | Yes, if ARR is your chosen proxy                    |
| Node.js application           | IIS proxies to Node.js                      | Usually, unless another IIS integration is used     |
| Python Flask/FastAPI          | IIS proxies to Python server                | Usually, unless another hosting integration is used |

## 3. What determines whether ARR is needed?

Ask this question:

Can IIS serve the application directly, or does it need to forward HTTP requests to another server?

Can IIS handle the application through its own hosting integration?

Yes

No ARR required

ASP.NET, static files, ANCM

No

Use a proxy

ARR or another supported forwarding mechanism

## 4. How does this relate to Shibboleth?

Shibboleth SP integrates with IIS regardless of whether ARR is used.

For example:

ASP.NET Framework application:

```
Browser → IIS + Shibboleth → ASP.NET
```

Your Spring Boot application:

```
Browser → IIS + Shibboleth → ARR → Spring Boot
```

In both cases, Shibboleth authenticates the user and can expose the authenticated identity to the application. The difference is how the application receives the request.

## 5. Could Spring Boot run without ARR?

Absolutely. You have several architectural choices:

- Keep IIS and Shibboleth: Use ARR or another IIS-compatible reverse proxy to reach Spring Boot.

- Use Apache HTTP Server with Shibboleth Native SP: Apache can use `mod_proxy` to forward requests to Spring Boot without IIS or ARR.

- Let Spring Boot be the SAML SP: Use Spring Security SAML2 directly with Keycloak or Shibboleth IdP, eliminating IIS, Native Shibboleth SP, and ARR.

For your `SpringBoot_No_SAML_App2`, ARR is not a SAML requirement. It's the reverse-proxy mechanism you chose because Spring Boot runs outside IIS.

That's why your ASP.NET applications may work with Shibboleth without ARR, while your Spring Boot application uses it.
