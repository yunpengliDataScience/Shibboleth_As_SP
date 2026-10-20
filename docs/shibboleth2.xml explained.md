Think of **`shibboleth2.xml` as the main configuration file for the Shibboleth Service Provider (SP)**.

In your architecture:

```
Browser
   ↓
IIS
   ↓
Shibboleth SP  ← shibboleth2.xml controls this
   ↕ SAML
Keycloak
   ↓
IIS / ARR
   ↓
Spring Boot
```

It tells Shibboleth things such as **who this SP is, which IdP to use, which URLs require authentication, how sessions work, how Shibboleth integrates with IIS, and how to expose the authenticated identity**.

### In your setup, the important parts are:

**1. Defines the identity of your Shibboleth SP**

```
<ApplicationDefaults
    entityID="http://localhost:8088/shibboleth"
    REMOTE_USER="uid">
```

`entityID` is essentially the SAML identity/name of your application from Keycloak's perspective:

```
Shibboleth says:
"I am http://localhost:8088/shibboleth"
                 ↓
Keycloak has a SAML client with the same Client ID
```

`REMOTE_USER="uid"` says that the `uid` attribute should be considered the preferred username.

---

**2. Tells Shibboleth which IdP to use**

```
<SSO entityID="https://localhost:9991/realms/MySecurityRealm">
    SAML2
</SSO>
```

This tells Shibboleth:

> When authentication is needed, use this Keycloak realm as the SAML Identity Provider.

So:

```
User requests application
        ↓
Shibboleth sees no session
        ↓
<SSO> tells Shibboleth
which IdP to use
        ↓
Keycloak
```

---

**3. Tells Shibboleth about Keycloak's SAML metadata**

```
<MetadataProvider
    type="XML"
    path="keycloak-metadata.xml"/>
```

The metadata contains information Shibboleth needs about Keycloak, such as its SAML endpoints and signing certificate.

Conceptually:

```
shibboleth2.xml
       │
       └── MetadataProvider
                 ↓
       keycloak-metadata.xml
                 ↓
        "Here is how to talk
         securely to Keycloak"
```

---

**4. Controls Shibboleth sessions**

For example:

```
<Sessions
    lifetime="28800"
    timeout="3600"
    relayState="ss:mem"
    checkAddress="false"
    handlerSSL="false">
```

This controls things such as session lifetime, inactivity timeout, and handler behavior.

Once Keycloak authenticates the user:

```
Keycloak
   ↓ SAML Response
Shibboleth
   ↓
creates Shibboleth session
   ↓
_shibsession_... cookie
```

That's the `_shibsession_...` cookie you saw while debugging.

---

**5. Determines which IIS requests require authentication**

Your `RequestMapper` contains something like:

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

The important setting is:

```
requireSession="true"
```

It means:

```
http://localhost:8088/
          ↓
Shibboleth session?
     /          \
   NO            YES
   ↓              ↓
Keycloak        Allow request
login
```

Without this, Shibboleth could be installed and configured but not necessarily force users to authenticate for the application.

---

**6. Connects Shibboleth to your IIS site**

This section was especially important in your setup:

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

You discovered that:

```
IIS ShibbolethDemo Site ID = 2
```

so Shibboleth needs:

```
<Site id="2" .../>
```

This connects the IIS site with the Shibboleth configuration.

And in our current IIS→ARR→Spring design, `useHeaders="true"` is what allowed Spring eventually to see:

```
uid: yunpeng.li
```

although, as discussed, Shibboleth normally prefers server variables when the application can consume them directly.

---

### What `shibboleth2.xml` does NOT do

This distinction is useful.

`shibboleth2.xml` does **not** define what the Keycloak `uid` SAML attribute looks like. That matching is in:

```
attribute-map.xml
```

For example:

```
<Attribute
    name="uid"
    nameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:basic"
    id="uid"/>
```

And `shibboleth2.xml` does **not** configure your Spring application or ARR reverse proxy. Those are separate:

```
shibboleth2.xml
      ↓
Shibboleth behavior

attribute-map.xml
      ↓
SAML attribute mapping

IIS URL Rewrite + ARR
      ↓
reverse proxy to Spring

application.yml / Java
      ↓
Spring application behavior
```

So the simplest way to remember it is:

> **`shibboleth2.xml` is the master configuration file that tells the Shibboleth SP how to behave.**

For your project specifically:

```
shibboleth2.xml
       │
       ├── Who am I?
       │     → entityID
       │
       ├── Who authenticates users?
       │     → Keycloak <SSO>
       │
       ├── How do I know about Keycloak?
       │     → MetadataProvider
       │
       ├── Which pages require login?
       │     → RequestMapper
       │
       ├── How long does login last?
       │     → Sessions
       │
       ├── What is the username?
       │     → REMOTE_USER="uid"
       │
       └── Which IIS site am I protecting?
             → <ISAPI><Site id="2">
```

Then `attribute-map.xml` answers the separate question: **“When Keycloak sends a SAML attribute called `uid`, what should Shibboleth call/use it as?”**



--------------------

`<ISAPI>` in `shibboleth2.xml` is specifically about **how the Shibboleth SP integrates with Microsoft IIS**.

In your architecture, think of it as the configuration for this connection:

```
             IIS
              │
              │ Shibboleth IIS module
              ▼
        Shibboleth SP
              │
         shibboleth2.xml
              │
          <ISAPI>
```

`ISAPI` stands for **Internet Server Application Programming Interface**, Microsoft's native IIS extension mechanism.

Your current configuration is approximately:

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

### `<Site>` — which IIS site?

This is probably the most important part for understanding `<ISAPI>`:

```
<Site
    id="2"
    name="localhost"
    scheme="http"
    port="8088"/>
```

Your IIS has a website:

```
IIS
└── Sites
      └── ShibbolethDemo
             Site ID = 2
             Port    = 8088
```

So:

```
id="2"
```

means:

> This configuration corresponds to IIS Site ID 2.

The `name` is the hostname:

```
name="localhost"
```

and these describe the external request:

```
scheme="http"
port="8088"
```

Together:

```
http://localhost:8088
```

This matters because Shibboleth has to reconstruct the correct public URL when it generates SAML URLs such as the ACS.

For example:

```
http://localhost:8088/Shibboleth.sso/SAML2/POST
```

---

### `normalizeRequest="true"`

```
normalizeRequest="true"
```

tells the IIS integration to normalize request information before Shibboleth uses it.

Conceptually, IIS provides information such as:

```
Host
Port
Protocol
URL
```

and Shibboleth needs a consistent representation of the request to decide things like:

```
Which application is this?

Does this URL require authentication?

What URL should I return to after authentication?

What should the ACS URL be?
```

For your configuration, leaving:

```
normalizeRequest="true"
```

is appropriate.

---

### `safeHeaderNames="true"`

This setting is related to how Shibboleth safely represents attribute/header names:

```
safeHeaderNames="true"
```

Shibboleth attributes can have names that aren't necessarily ideal as HTTP/IIS header names.

For example, suppose an internal attribute had a name involving characters that don't map cleanly into an HTTP header. `safeHeaderNames` helps Shibboleth expose them using safer names.

Your attribute is simply:

```
uid
```

so it's already simple, but there's normally no reason to turn this off.

---

### `useVariables="true"`

This is particularly important.

```
useVariables="true"
```

means:

> Make Shibboleth information available through **IIS server variables**.

For example, after successful authentication Shibboleth may make information available to an IIS-hosted application such as:

```
uid = yunpeng.li
```

through the IIS server-variable mechanism.

This is Shibboleth's preferred IIS approach.

Conceptually:

```
Keycloak
   ↓ SAML
Shibboleth
   ↓
uid = yunpeng.li
   ↓
IIS Server Variable
```

If your application were directly integrated with IIS and able to read IIS server variables, this would be ideal.

But your application isn't running inside IIS.

It's here:

```
IIS :8088
     │
     │ HTTP
     ▼
Spring Boot :9091
```

That creates the complication we discovered.

---

### `useHeaders="true"`

This tells Shibboleth to also expose information using **HTTP headers**.

That's why your Spring diagnostic eventually showed:

```
"uid":["yunpeng.li"]
```

The path is essentially:

```
Keycloak
   │
   │ SAML
   ▼
Shibboleth
   │
   │ uid = yunpeng.li
   ▼
IIS
   │
   │ HTTP header
   │ uid: yunpeng.li
   ▼
ARR
   │
   │ HTTP
   ▼
Spring Boot
```

Then Java can do:

```
String uid = request.getHeader("uid");
```

and get:

```
yunpeng.li
```

This is why changing:

```
useHeaders="false"
```

to:

```
useHeaders="true"
```

was such an important diagnostic result for your architecture.

### Why have both?

Your current setup says:

```
useVariables="true"
useHeaders="true"
```

so Shibboleth can expose information in both forms:

```
                    Shibboleth
                    uid = yunpeng.li
                       /       \
                      /         \
                     ▼           ▼
             IIS variable     HTTP header
                     │           │
        useVariables=true   useHeaders=true
                                 │
                                 ▼
                                ARR
                                 │
                                 ▼
                              Spring
```

The important distinction is:

```
useVariables
    =
IIS-native mechanism

useHeaders
    =
HTTP header mechanism
```

Shibboleth prefers the former where possible because HTTP headers introduce spoofing/trust concerns. In your particular ARR→Spring architecture, however, Spring naturally receives HTTP headers while it does not naturally receive IIS server variables.

---

### `<ISAPI>` does not perform authentication itself

This is another important distinction.

`<ISAPI>` does **not** mean:

```
Authenticate against Keycloak
```

That job comes from other parts of `shibboleth2.xml`, such as:

```
<SSO entityID="https://localhost:9991/realms/MySecurityRealm">
    SAML2
</SSO>
```

and:

```
<Host
    name="localhost"
    scheme="http"
    port="8088"
    authType="shibboleth"
    requireSession="true"/>
```

`<ISAPI>` is primarily about:

> **How does Shibboleth integrate with IIS?**

So you can mentally divide the configuration like this:

```
<ApplicationDefaults>
        │
        └── Who is my SP?

<Sessions>
        │
        └── How do SAML sessions work?

<SSO>
        │
        └── Which IdP should authenticate?
            → Keycloak

<MetadataProvider>
        │
        └── How do I know about Keycloak?

<RequestMapper>
        │
        └── Which URLs require authentication?

<ISAPI>
        │
        └── How do I integrate with IIS?
```

For your configuration specifically, I would annotate it like this:

```
<ISAPI
    normalizeRequest="true"
    <!-- Normalize IIS request information -->

    safeHeaderNames="true"
    <!-- Use safe names when exposing attributes -->

    useVariables="true"
    <!-- Expose Shibboleth attributes as IIS server variables -->

    useHeaders="true">
    <!-- Also expose them as HTTP headers.
         Needed in our ARR -> Spring design. -->

    <Site
        id="2"
        <!-- IIS Site ID -->

        name="localhost"
        <!-- Public hostname -->

        scheme="http"
        <!-- Development protocol -->

        port="8088"/>
        <!-- Public IIS port -->

</ISAPI>
```

The easiest way to remember it is:

> **`<ISAPI>` is the bridge configuration between IIS and Shibboleth.**

And in your case, the particularly important setting is `useHeaders="true"` because your application is **not inside IIS**—it's a separate Spring Boot process behind ARR.


